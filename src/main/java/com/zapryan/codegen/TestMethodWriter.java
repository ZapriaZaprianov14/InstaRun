package com.zapryan.codegen;

import com.zapryan.bootstrap.Outcome;
import com.zapryan.launch.MethodInput;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ValueSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes a test method that repeats a recorded call and checks it does what it did when it was recorded: given the
 * same receiver and arguments, when the method is called, then it returns the same values or throws the same
 * exception.
 */
public final class TestMethodWriter {

    private static final String INDENT = "    ";

    private TestMethodWriter() {
    }

    /**
     * @param outcome how the recorded call went: {@link Outcome.Returned}, {@link Outcome.ReturnedVoid} or
     *                {@link Outcome.Threw}
     */
    public static GeneratedTest write(TestSubject subject, MethodInput input, Outcome outcome, TestFramework framework,
                                      CodeContext context) {
        MethodDescriptor method = subject.method();
        VariableNames names = new VariableNames();
        InputWriter inputs = new InputWriter(context, names);
        String target = method.isStatic() ? method.getDeclaringClassFqn()
                : inputs.receiver(method, receiverOf(input), subject.receiverTypeText());
        String call = target + "." + method.getMethodName() + "(" + inputs.arguments(method, input.getArguments()) + ")";

        Set<String> staticImports = new TreeSet<>();
        List<String> when = new ArrayList<>();
        List<String> then = new ArrayList<>();
        String expectedException = null;
        if (outcome instanceof Outcome.Returned) {
            String result = names.allocate("result");
            when.add(subject.resultTypeText() + " " + result + " = " + call + ";");
            AssertionWriter assertions = new AssertionWriter(framework, context);
            assertions.write(result, subject.resultShape(), ((Outcome.Returned) outcome).getValue());
            then.add("// " + context.comments().recordedValues());
            then.addAll(assertions.getStatements());
            staticImports.addAll(assertions.getAssertions());
        } else if (outcome instanceof Outcome.ReturnedVoid) {
            when.add(call + ";");
            then.add("// " + context.comments().returnedNothing());
        } else if (outcome instanceof Outcome.Threw) {
            Outcome.Threw threw = (Outcome.Threw) outcome;
            String exceptionClass = referableException(threw, context);
            if (framework.hasAssertThrows()) {
                String exception = names.allocate("exception");
                when.add(exceptionClass + " " + exception + " = assertThrows(" + exceptionClass + ".class, () -> "
                        + call + ");");
                staticImports.add("assertThrows");
                then.add("// " + context.comments().recordedValues());
                if (threw.getMessage() != null) {
                    then.add("assertEquals(" + JavaLiterals.quote(threw.getMessage()) + ", " + exception
                            + ".getMessage());");
                    staticImports.add("assertEquals");
                } else {
                    then.add("assertNull(" + exception + ".getMessage());");
                    staticImports.add("assertNull");
                }
            } else {
                expectedException = exceptionClass;
                when.add(call + ";");
            }
        } else {
            throw new IllegalArgumentException("No test can be generated from " + outcome);
        }

        StringBuilder text = new StringBuilder();
        text.append('@').append(framework.getTestAnnotation());
        if (expectedException != null) {
            text.append("(expected = ").append(expectedException).append(".class)");
        }
        text.append('\n');
        if (framework.needsPublicMembers()) {
            text.append("public ");
        }
        text.append("void ").append(subject.testMethodName()).append("()");
        if (subject.throwsCheckedExceptions()) {
            text.append(" throws java.lang.Exception");
        }
        text.append(" {\n");
        appendSection(text, inputs.getStatements());
        appendSection(text, when);
        appendSection(text, then);
        text.append("}\n");
        return new GeneratedTest(text.toString(), staticImports);
    }

    private static ValueSpec.Constructed receiverOf(MethodInput input) {
        if (!(input.getReceiver() instanceof ValueSpec.Constructed)) {
            throw new IllegalArgumentException("An instance method needs a receiver built by a constructor");
        }
        return (ValueSpec.Constructed) input.getReceiver();
    }

    /** The exception's own class if the test can name it, or else the nearest superclass it can. */
    private static String referableException(Outcome.Threw threw, CodeContext context) {
        for (String exceptionClass : threw.getExceptionClasses()) {
            String name = context.referableName(exceptionClass);
            if (name != null) {
                return name;
            }
        }
        return "java.lang.Throwable";
    }

    /** Sections are separated by an empty line. */
    private static void appendSection(StringBuilder text, List<String> statements) {
        if (statements.isEmpty()) {
            return;
        }
        if (text.charAt(text.length() - 2) != '{') {
            text.append('\n');
        }
        for (String statement : statements) {
            text.append(INDENT).append(statement).append('\n');
        }
    }
}
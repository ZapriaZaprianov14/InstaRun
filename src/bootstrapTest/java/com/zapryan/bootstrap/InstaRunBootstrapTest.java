package com.zapryan.bootstrap;

import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.ValueSpec;
import org.junit.Test;

import java.io.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstaRunBootstrapTest {

    private static final String TARGETS = "com.zapryan.bootstrap.fixtures.TargetMethods";
    private static final String GREETER = TARGETS + "$Greeter";
    private static final String BEANS = "com.zapryan.bootstrap.fixtures.BeanTargets";
    private static final String PERSON = BEANS + "$Person";
    private static final String TEAM = BEANS + "$Team";
    private static final String STATUS = BEANS + "$Status";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private File outcomeFile;

    @Test
    public void printsTheReturnValue() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "answer"));
        assertEquals("[InstaRun] Result: 42", stdout());
    }

    @Test
    public void printsNullReturnValues() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "nothing"));
        assertEquals("[InstaRun] Result: null", stdout());
    }

    @Test
    public void printsVoidForVoidMethods() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "noResult"));
        assertEquals("[InstaRun] Result: void", stdout());
    }

    @Test
    public void findsNestedClassesByBinaryName() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS + "$Nested", "name"));
        assertEquals("[InstaRun] Result: nested", stdout());
    }

    @Test
    public void invokesPrivateMethods() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "hidden"));
        assertEquals("[InstaRun] Result: hidden", stdout());
    }

    @Test
    public void invokesPublicMethodsOfNonPublicClasses() throws Throwable {
        assertEquals(0, invokeStatic("com.zapryan.bootstrap.fixtures.PackagePrivateTarget", "greet"));
        assertEquals("[InstaRun] Result: hello", stdout());
    }

    @Test
    public void passesArguments() throws Throwable {
        assertEquals(0, invoke(TARGETS, "repeat", types("java.lang.String", "int"), null, literal("ab"), literal(3)));
        assertEquals("[InstaRun] Result: ababab", stdout());
    }

    @Test
    public void passesEveryPrimitiveType() throws Throwable {
        assertEquals(0, invoke(TARGETS, "primitives",
                types("boolean", "byte", "char", "short", "int", "long", "float", "double"), null,
                literal(true), literal((byte) 1), literal('c'), literal((short) 2), literal(3), literal(4L),
                literal(5.5f), literal(6.5d)));
        assertEquals("[InstaRun] Result: true 1 c 2 3 4 5.5 6.5", stdout());
    }

    @Test
    public void passesNullToWrapperParameters() throws Throwable {
        assertEquals(0, invoke(TARGETS, "describe", types("java.lang.Integer"), null, new ValueSpec.Null()));
        assertEquals("[InstaRun] Result: no value", stdout());
    }

    @Test
    public void callsInstanceMethodsOnAReceiverBuiltWithTheNoArgumentConstructor() throws Throwable {
        assertEquals(0, invoke(TARGETS, "instanceMethod", types(), receiver(TARGETS, types())));
        assertEquals("[InstaRun] Result: 7", stdout());
    }

    @Test
    public void buildsTheReceiverWithTheChosenConstructorEvenWhenItIsPrivate() throws Throwable {
        ValueSpec receiver = receiver(GREETER, types("java.lang.String"), literal("Hi"));

        assertEquals(0, invoke(GREETER, "greet", types("java.lang.String"), receiver, literal("Ann")));
        assertEquals("[InstaRun] Result: Hi, Ann", stdout());
    }

    @Test
    public void rethrowsTheMethodsOwnExceptionUnwrapped() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> invokeStatic(TARGETS, "fail"));

        assertEquals("boom", thrown.getMessage());
    }

    @Test
    public void rethrowsTheReceiverConstructorsOwnExceptionUnwrapped() {
        String exploding = TARGETS + "$Exploding";

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> invoke(exploding, "run", types(), receiver(exploding, types())));

        assertEquals("constructor boom", thrown.getMessage());
    }

    @Test
    public void reportsAMissingClass() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invokeStatic("com.example.Missing", "run"));
        assertTrue(stderr(), stderr().contains("Class com.example.Missing was not found"));
    }

    @Test
    public void reportsAMissingMethodWithItsParameterTypes() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invoke(TARGETS, "repeat", types("int"), null, literal(1)));
        assertTrue(stderr(), stderr().contains(TARGETS + "#repeat(int) was not found"));
    }

    @Test
    public void reportsAMissingConstructor() throws Throwable {
        ValueSpec receiver = receiver(GREETER, types("int"), literal(1));

        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invoke(GREETER, "greet", types("java.lang.String"), receiver, literal("Ann")));
        assertTrue(stderr(), stderr().contains("Constructor " + GREETER + "(int) was not found"));
    }

    @Test
    public void reportsAnInstanceMethodWithoutAReceiver() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invokeStatic(TARGETS, "instanceMethod"));
        assertTrue(stderr(), stderr().contains(TARGETS + "#instanceMethod() is not static"));
    }

    @Test
    public void buildsBeansWithAConstructorAndThenTheirSetters() throws Throwable {
        ValueSpec person = new ValueSpec.Constructed(PERSON, types("java.lang.String", "int"),
                Arrays.asList(literal("Ann"), literal(31)),
                Collections.singletonList(setter(PERSON, "setEmail", "java.lang.String", literal("ann@example.com"))));

        assertEquals(0, invoke(BEANS, "describe", types(PERSON), null, person));
        assertEquals("[InstaRun] Result: Ann (31) ann@example.com", stdout());
    }

    @Test
    public void callsInheritedSettersByTheirErasureAndPrivateSetters() throws Throwable {
        ValueSpec label = new ValueSpec.Constructed(BEANS + "$Label", types(), Collections.<ValueSpec>emptyList(),
                Arrays.asList(
                        setter(BEANS + "$Named", "setValue", "java.lang.Object", literal("shown")),
                        setter(BEANS + "$Label", "setSecret", "java.lang.String", literal("hidden"))));

        assertEquals(0, invoke(BEANS, "label", types(BEANS + "$Label"), null, label));
        assertEquals("[InstaRun] Result: shown hidden", stdout());
    }

    @Test
    public void buildsNestedBeansCollectionsAndEnums() throws Throwable {
        ValueSpec member = new ValueSpec.Constructed(PERSON, types("java.lang.String", "int"),
                Arrays.asList(literal("Bo"), literal(40)));
        ValueSpec team = new ValueSpec.Constructed(TEAM, types(), Collections.<ValueSpec>emptyList(), Arrays.asList(
                setter(TEAM, "setMembers", "java.util.List",
                        new ValueSpec.CollectionOf("java.util.ArrayList", Collections.singletonList(member))),
                setter(TEAM, "setTags", "java.util.Collection",
                        new ValueSpec.CollectionOf("java.util.TreeSet", Arrays.asList(literal("z"), literal("x")))),
                setter(TEAM, "setStatus", STATUS, new ValueSpec.EnumConstant(STATUS, "ACTIVE"))));

        assertEquals(0, invoke(BEANS, "describeTeam", types(TEAM), null, team));
        assertEquals("[InstaRun] Result: [Bo (40) null] TreeSet[x, z] ACTIVE", stdout());
    }

    @Test
    public void fillsCollectionsOfTheRequestedClassInOrder() throws Throwable {
        ValueSpec tags = new ValueSpec.CollectionOf("java.util.LinkedHashSet",
                Arrays.asList(literal("b"), literal("a"), literal("b")));

        assertEquals(0, invoke(BEANS, "describeCollection", types("java.util.Collection"), null, tags));
        assertEquals("[InstaRun] Result: LinkedHashSet[b, a]", stdout());
    }

    @Test
    public void findsEnumConstantsThatHaveABody() throws Throwable {
        assertEquals(0, invoke(BEANS, "status", types(STATUS), null, new ValueSpec.EnumConstant(STATUS, "RETIRED")));
        assertEquals("[InstaRun] Result: RETIRED gone", stdout());
    }

    @Test
    public void buildsArraysOfPrimitives() throws Throwable {
        ValueSpec numbers = new ValueSpec.ArrayOf("int", Arrays.asList(literal(1), literal(2), literal(3)));

        assertEquals(0, invoke(BEANS, "sum", types("int[]"), null, numbers));
        assertEquals("[InstaRun] Result: sum 6", stdout());
    }

    @Test
    public void buildsArraysOfArrays() throws Throwable {
        ValueSpec rows = new ValueSpec.ArrayOf("java.lang.String[]", Arrays.<ValueSpec>asList(
                new ValueSpec.ArrayOf("java.lang.String", Arrays.asList(literal("a"), literal("b"))),
                new ValueSpec.ArrayOf("java.lang.String", Collections.singletonList(literal("c")))));

        assertEquals(0, invoke(BEANS, "grid", types("java.lang.String[][]"), null, rows));
        assertEquals("[InstaRun] Result: a,b;c;", stdout());
    }

    @Test
    public void passesBigDecimalsWithTheirExactScale() throws Throwable {
        assertEquals(0, invoke(BEANS, "price", types("java.math.BigDecimal"), null, literal(new BigDecimal("0.10"))));
        assertEquals("[InstaRun] Result: 0.10 scale 2", stdout());
    }

    @Test
    public void rethrowsASettersOwnExceptionUnwrapped() {
        String fussy = BEANS + "$Fussy";
        ValueSpec bean = new ValueSpec.Constructed(fussy, types(), Collections.<ValueSpec>emptyList(),
                Collections.singletonList(setter(fussy, "setMood", "java.lang.String", literal("grumpy"))));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> invoke(BEANS, "fussy", types(fussy), null, bean));

        assertEquals("not in the mood: grumpy", thrown.getMessage());
    }

    @Test
    public void reportsAMissingSetter() throws Throwable {
        ValueSpec person = new ValueSpec.Constructed(PERSON, types("java.lang.String", "int"),
                Arrays.asList(literal("Ann"), literal(31)),
                Collections.singletonList(setter(PERSON, "setNickname", "java.lang.String", literal("A"))));

        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invoke(BEANS, "describe", types(PERSON), null, person));
        assertTrue(stderr(), stderr().contains("Setter " + PERSON + "#setNickname(java.lang.String) was not found"));
    }

    @Test
    public void reportsAMissingEnumConstant() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invoke(BEANS, "status", types(STATUS), null, new ValueSpec.EnumConstant(STATUS, "GONE")));
        assertTrue(stderr(), stderr().contains("Enum " + STATUS + " has no constant GONE"));
    }

    @Test
    public void reportsAnElementTheCollectionRefuses() throws Throwable {
        ValueSpec person = new ValueSpec.Constructed(PERSON, types("java.lang.String", "int"),
                Arrays.asList(literal("Ann"), literal(31)));
        ValueSpec notComparable = new ValueSpec.CollectionOf("java.util.TreeSet", Collections.singletonList(person));

        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invoke(BEANS, "describeCollection", types("java.util.Collection"), null, notComparable));
        assertTrue(stderr(), stderr().contains("The element at index 0 could not be added to a java.util.TreeSet"));
    }

    @Test
    public void reportsANullInAnArrayOfPrimitives() throws Throwable {
        ValueSpec numbers = new ValueSpec.ArrayOf("int", Arrays.asList(literal(1), new ValueSpec.Null()));

        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invoke(BEANS, "sum", types("int[]"), null, numbers));
        assertTrue(stderr(), stderr().contains("The element at index 1 cannot be stored in an array of int"));
    }

    @Test
    public void recordsTheReturnedValueForTestGeneration() throws Throwable {
        assertEquals(0, invokeRecording(TARGETS, "answer", types(), null));

        assertEquals(new Outcome.Returned(new Observation.Literal(42)), recordedOutcome());
        assertEquals("[InstaRun] Result: 42", stdout());
    }

    @Test
    public void recordsThatAVoidMethodReturned() throws Throwable {
        assertEquals(0, invokeRecording(TARGETS, "noResult", types(), null));

        assertEquals(new Outcome.ReturnedVoid(), recordedOutcome());
    }

    @Test
    public void recordsTheExceptionTheMethodThrewAndStillRethrowsIt() throws Throwable {
        assertThrows(IllegalStateException.class, () -> invokeRecording(TARGETS, "fail", types(), null));

        assertEquals(new Outcome.Threw(Arrays.asList("java.lang.IllegalStateException", "java.lang.RuntimeException",
                "java.lang.Exception", "java.lang.Throwable"), "boom"), recordedOutcome());
    }

    @Test
    public void recordsThatBuildingTheReceiverThrewRatherThanTheMethod() throws Throwable {
        String exploding = TARGETS + "$Exploding";

        assertThrows(IllegalStateException.class,
                () -> invokeRecording(exploding, "run", types(), receiver(exploding, types())));

        Outcome.SetupThrew outcome = (Outcome.SetupThrew) recordedOutcome();
        assertEquals("java.lang.IllegalStateException", outcome.getExceptionClasses().get(0));
        assertEquals("constructor boom", outcome.getMessage());
    }

    @Test
    public void recordsWhyTheCallCouldNotBeSetUp() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invokeRecording("com.example.Missing", "run", types(), null));

        assertEquals(new Outcome.NotRun("Class com.example.Missing was not found on the classpath."), recordedOutcome());
    }

    @Test
    public void writesNoOutcomeForAPlainRun() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "answer"));

        assertEquals(0, outcomeFile().length());
    }

    @Test
    public void reportsAMalformedRequest() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, run("not base64!"));
        assertTrue(stderr(), stderr().contains("Could not decode the invocation request"));
    }

    @Test
    public void reportsAMissingRequest() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, run());
        assertTrue(stderr(), stderr().contains("but got 0"));
    }

    private int invokeStatic(String className, String methodName) throws Throwable {
        return invoke(className, methodName, types(), null);
    }

    private int invoke(String className, String methodName, List<String> parameterTypes, ValueSpec receiver,
                       ValueSpec... arguments) throws Throwable {
        InvocationRequest request =
                new InvocationRequest(className, methodName, parameterTypes, receiver, Arrays.asList(arguments));
        return run(InvocationRequestCodec.encode(request));
    }

    /** Like {@link #invoke}, but as when a test is generated: the outcome goes to {@link #outcomeFile()}. */
    private int invokeRecording(String className, String methodName, List<String> parameterTypes, ValueSpec receiver,
                                ValueSpec... arguments) throws Throwable {
        InvocationRequest request = new InvocationRequest(className, methodName, parameterTypes, receiver,
                Arrays.asList(arguments), outcomeFile().getPath());
        return run(InvocationRequestCodec.encode(request));
    }

    private File outcomeFile() throws IOException {
        if (outcomeFile == null) {
            outcomeFile = File.createTempFile("instarun-outcome", ".ser");
            outcomeFile.deleteOnExit();
        }
        return outcomeFile;
    }

    private Outcome recordedOutcome() throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(outcomeFile()))) {
            return (Outcome) in.readObject();
        }
    }

    private int run(String... args) throws Throwable {
        return InstaRunBootstrap.run(args, new PrintStream(out, true), new PrintStream(err, true));
    }

    private static List<String> types(String... names) {
        return names.length == 0 ? Collections.<String>emptyList() : Arrays.asList(names);
    }

    private static ValueSpec literal(Serializable value) {
        return new ValueSpec.Literal(value);
    }

    private static ValueSpec receiver(String className, List<String> parameterTypes, ValueSpec... arguments) {
        return new ValueSpec.Constructed(className, parameterTypes, Arrays.asList(arguments));
    }

    private static SetterCall setter(String declaringClass, String methodName, String parameterType, ValueSpec value) {
        return new SetterCall(declaringClass, methodName, parameterType, value);
    }

    private String stdout() {
        return out.toString().trim();
    }

    private String stderr() {
        return err.toString().trim();
    }
}
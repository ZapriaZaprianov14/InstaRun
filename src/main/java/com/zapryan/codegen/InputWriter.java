package com.zapryan.codegen;

import com.zapryan.reflection.ConstructorDescriptor;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.SetterDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes the statements that rebuild a call's receiver and arguments from the recipes the run used, in the order the
 * run built them. Literals, nulls and enum constants are written where they are used; beans, collections and arrays
 * become local variables named after the parameter or property they are for.
 */
final class InputWriter {

    private final CodeContext context;
    private final VariableNames names;
    private final List<String> statements = new ArrayList<>();

    InputWriter(CodeContext context, VariableNames names) {
        this.context = context;
        this.names = names;
    }

    List<String> getStatements() {
        return statements;
    }

    /**
     * Declares the object an instance method is called on and returns its variable.
     *
     * @param typeText what to declare the variable as, e.g. {@code com.acme.Cache<?, ?>} for a generic class
     */
    String receiver(MethodDescriptor method, ValueSpec.Constructed spec, String typeText) {
        ConstructorDescriptor constructor = matching(method.getReceiver().getConstructors(), spec);
        String arguments = arguments(spec.getClassBinaryName(), "<init>", constructor.getParameters(),
                spec.getArguments());
        String className = method.getDeclaringClassFqn();
        String name = names.allocate(VariableNames.forClass(className.substring(className.lastIndexOf('.') + 1)));
        statements.add(typeText + " " + name + " = new " + creation(typeText) + "(" + arguments + ");");
        return name;
    }

    /** The expressions to call the method with, declaring the variables they need first. */
    String arguments(MethodDescriptor method, List<ValueSpec> values) {
        return arguments(method.getDeclaringClassBinaryName(), method.getMethodName(), method.getParameters(), values);
    }

    private String arguments(String classBinaryName, String methodName, List<ParameterDescriptor> parameters,
                             List<ValueSpec> values) {
        boolean overloaded = context.isOverloaded(classBinaryName, methodName, values.size());
        List<String> expressions = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            ParameterDescriptor parameter = parameters.get(i);
            expressions.add(expression(values.get(i), parameter.getType(), parameter.getName(), overloaded));
        }
        return String.join(", ", expressions);
    }

    /**
     * The value as an expression for a slot of the given type. When the slot belongs to an overloaded method, a null,
     * or a literal of another type than the slot's, e.g. 5 for an Integer, is cast to the slot's type, so the call
     * picks the same method the run did; a variable already has exactly the slot's type.
     */
    private String expression(ValueSpec value, TypeDescriptor type, String nameHint, boolean overloaded) {
        if (value instanceof ValueSpec.Literal) {
            Object literal = ((ValueSpec.Literal) value).getValue();
            String expression = JavaLiterals.of(literal);
            return overloaded && !JavaLiterals.typeOf(literal).equals(type.getCanonicalText())
                    ? JavaLiterals.cast(type.getCanonicalText(), expression)
                    : expression;
        }
        if (value instanceof ValueSpec.Null) {
            return overloaded ? JavaLiterals.cast(type.getCanonicalText(), "null") : "null";
        }
        if (value instanceof ValueSpec.EnumConstant) {
            return type.getCanonicalText() + "." + ((ValueSpec.EnumConstant) value).getConstantName();
        }
        if (value instanceof ValueSpec.Constructed) {
            return bean((ValueSpec.Constructed) value, (TypeDescriptor.BeanType) type, nameHint);
        }
        if (value instanceof ValueSpec.ArrayOf) {
            return array((ValueSpec.ArrayOf) value, (TypeDescriptor.ArrayType) type, nameHint);
        }
        if (value instanceof ValueSpec.CollectionOf) {
            return collection((ValueSpec.CollectionOf) value, (TypeDescriptor.CollectionType) type, nameHint);
        }
        throw new IllegalArgumentException("Unknown value recipe: " + value);
    }

    private String bean(ValueSpec.Constructed spec, TypeDescriptor.BeanType type, String nameHint) {
        ConstructorDescriptor constructor = matching(type.getConstructors(), spec);
        // The arguments come first, since building them may declare variables of their own.
        String arguments = arguments(spec.getClassBinaryName(), "<init>", constructor.getParameters(),
                spec.getArguments());
        String name = names.allocate(nameHint);
        String typeText = type.getCanonicalText();
        statements.add(typeText + " " + name + " = new " + creation(typeText) + "(" + arguments + ");");
        for (SetterCall call : spec.getSetterCalls()) {
            SetterDescriptor setter = setterFor(type, call);
            boolean overloaded = context.isOverloaded(call.getDeclaringClassBinaryName(), call.getMethodName(), 1);
            String value = expression(call.getValue(), setter.getType(), setter.getPropertyName(), overloaded);
            statements.add(name + "." + call.getMethodName() + "(" + value + ");");
        }
        return name;
    }

    private String array(ValueSpec.ArrayOf spec, TypeDescriptor.ArrayType type, String nameHint) {
        String elements = elements(spec.getElements(), type.getElementType());
        String name = names.allocate(nameHint);
        // An array of a generic type can only be created raw, e.g. new java.util.List[]{...}.
        String component = withoutTypeArguments(type.getElementType().getCanonicalText());
        statements.add(type.getCanonicalText() + " " + name + " = new " + component + "[]{" + elements + "};");
        return name;
    }

    private String collection(ValueSpec.CollectionOf spec, TypeDescriptor.CollectionType type, String nameHint) {
        List<ValueSpec> values = spec.getElements();
        String elements = elements(values, type.getElementType());
        if (values.size() == 1 && values.get(0) instanceof ValueSpec.Null) {
            // Arrays.asList(null) would pass a null array rather than one null element.
            elements = JavaLiterals.cast(type.getElementType().getCanonicalText(), elements);
        }
        String name = names.allocate(nameHint);
        String implementation = spec.getCollectionClassBinaryName();
        String initializer = values.isEmpty()
                ? "new " + implementation + "<>()"
                : "new " + implementation + "<>(java.util.Arrays.asList(" + elements + "))";
        statements.add(type.getCanonicalText() + " " + name + " = " + initializer + ";");
        return name;
    }

    private String elements(List<ValueSpec> values, TypeDescriptor elementType) {
        String hint = elementType instanceof TypeDescriptor.BeanType
                ? VariableNames.forClass(simpleName(((TypeDescriptor.BeanType) elementType).getCanonicalText()))
                : "element";
        return values.stream()
                .map(value -> expression(value, elementType, hint, false))
                .collect(Collectors.joining(", "));
    }

    private static ConstructorDescriptor matching(List<ConstructorDescriptor> constructors,
                                                  ValueSpec.Constructed spec) {
        for (ConstructorDescriptor constructor : constructors) {
            List<String> parameterTypes =
                    constructor.getParameters().stream().map(ParameterDescriptor::getErasedTypeName).toList();
            if (parameterTypes.equals(spec.getParameterTypeNames())) {
                return constructor;
            }
        }
        throw new IllegalStateException("No constructor of " + spec.getClassBinaryName() + " takes "
                + spec.getParameterTypeNames());
    }

    private static SetterDescriptor setterFor(TypeDescriptor.BeanType type, SetterCall call) {
        for (SetterDescriptor setter : type.getSetters()) {
            if (setter.getMethodName().equals(call.getMethodName())
                    && setter.getDeclaringClassBinaryName().equals(call.getDeclaringClassBinaryName())) {
                return setter;
            }
        }
        throw new IllegalStateException("No setter " + call.getMethodName() + " on " + type.getBinaryName());
    }

    /** What follows {@code new}: the class, with a diamond if the declared type has type arguments. */
    private static String creation(String typeText) {
        int arguments = typeText.indexOf('<');
        return arguments < 0 ? typeText : typeText.substring(0, arguments) + "<>";
    }

    /** {@code java.util.Map<K, V>[]} becomes {@code java.util.Map[]}. */
    static String withoutTypeArguments(String typeText) {
        StringBuilder result = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < typeText.length(); i++) {
            char c = typeText.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String simpleName(String typeText) {
        String erased = withoutTypeArguments(typeText);
        return erased.substring(erased.lastIndexOf('.') + 1);
    }
}
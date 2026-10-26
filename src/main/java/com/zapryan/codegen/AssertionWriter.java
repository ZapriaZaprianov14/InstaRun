package com.zapryan.codegen;

import com.zapryan.bootstrap.Observation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes assertions that check a value against what the run recorded for it. Each expression is checked in a way its
 * compile-time type allows: literals with assertEquals, objects through the getters of their declared type, lists and
 * arrays element by element, sets and maps by size and contents. Values the type gives no way to read are only checked
 * not to be null.
 */
final class AssertionWriter {

    static final int MAX_ASSERTIONS = 50;

    private final TestFramework framework;
    private final CodeContext context;
    private final List<String> statements = new ArrayList<>();
    private final Set<String> assertions = new TreeSet<>();
    private int count;

    AssertionWriter(TestFramework framework, CodeContext context) {
        this.framework = framework;
        this.context = context;
    }

    List<String> getStatements() {
        return statements;
    }

    /** The assertion methods used, to import statically from the framework's assertions class. */
    Set<String> getAssertions() {
        return assertions;
    }

    void write(String expression, TypeShape type, Observation observed) {
        if (count > MAX_ASSERTIONS) {
            return;
        }
        if (count == MAX_ASSERTIONS) {
            statements.add("// " + context.comments().moreValuesNotChecked());
            count++;
            return;
        }
        if (observed instanceof Observation.Null) {
            if (type.getKind() != TypeShape.Kind.PRIMITIVE) {
                assertion("assertNull", expression);
            }
        } else if (observed instanceof Observation.Literal) {
            literal(expression, type, ((Observation.Literal) observed).getValue());
        } else if (observed instanceof Observation.EnumConstant) {
            enumConstant(expression, (Observation.EnumConstant) observed);
        } else if (observed instanceof Observation.Text) {
            assertion("assertEquals", JavaLiterals.quote(((Observation.Text) observed).getText()),
                    expression + ".toString()");
        } else if (observed instanceof Observation.Sequence) {
            sequence(expression, type, (Observation.Sequence) observed);
        } else if (observed instanceof Observation.MapOf) {
            map(expression, type, (Observation.MapOf) observed);
        } else if (observed instanceof Observation.Bean) {
            bean(expression, type, (Observation.Bean) observed);
        } else if (observed instanceof Observation.Unrecorded) {
            unrecorded(expression, type, (Observation.Unrecorded) observed);
        }
    }

    private void literal(String expression, TypeShape type, Object value) {
        boolean primitive = type.getKind() == TypeShape.Kind.PRIMITIVE;
        if (value instanceof Boolean && (primitive || type.getKind() == TypeShape.Kind.BOXED)) {
            assertion((Boolean) value ? "assertTrue" : "assertFalse", expression);
        } else if (!primitive) {
            // A boxed expected value keeps JUnit 4's assertEquals(long, long) and assertEquals(Object, Object) apart.
            assertion("assertEquals", JavaLiterals.boxed(value), expression);
        } else if (framework.needsFloatingPointDelta() && value instanceof Float) {
            assertion("assertEquals", JavaLiterals.of(value), expression, "0.0f");
        } else if (framework.needsFloatingPointDelta() && value instanceof Double) {
            assertion("assertEquals", JavaLiterals.of(value), expression, "0.0");
        } else {
            assertion("assertEquals", JavaLiterals.of(value), expression);
        }
    }

    private void enumConstant(String expression, Observation.EnumConstant constant) {
        String enumName = context.referableName(constant.getEnumClassBinaryName());
        if (enumName != null) {
            assertion("assertEquals", enumName + "." + constant.getConstantName(), expression);
        } else {
            assertion("assertEquals", JavaLiterals.quote(constant.getConstantName()),
                    "((java.lang.Enum<?>) " + expression + ").name()");
        }
    }

    private void sequence(String expression, TypeShape type, Observation.Sequence sequence) {
        switch (type.getKind()) {
            case ARRAY -> array(expression, type, sequence);
            case LIST, SET, COLLECTION -> collection(expression, type, sequence);
            default -> assertion("assertNotNull", expression);
        }
    }

    private void array(String expression, TypeShape type, Observation.Sequence array) {
        TypeShape component = elementOf(type);
        if (array.getSize() == 0) {
            assertion("assertEquals", "0", expression + ".length");
            return;
        }
        List<String> elements = array.isComplete() && isPlainComponent(component) ? simple(array.getElements()) : null;
        if (elements != null) {
            String expected = "new " + component.getText() + "[]{" + String.join(", ", elements) + "}";
            String delta = component.getText().equals("double") ? "0.0" : component.getText().equals("float") ? "0.0f"
                    : null;
            if (delta != null && framework.needsFloatingPointDelta()) {
                assertion("assertArrayEquals", expected, expression, delta);
            } else {
                assertion("assertArrayEquals", expected, expression);
            }
            return;
        }
        assertion("assertEquals", String.valueOf(array.getSize()), expression + ".length");
        for (int i = 0; i < array.getElements().size(); i++) {
            write(expression + "[" + i + "]", component, array.getElements().get(i));
        }
    }

    private void collection(String expression, TypeShape type, Observation.Sequence collection) {
        if (collection.getSize() == 0) {
            assertion("assertTrue", expression + ".isEmpty()");
            return;
        }
        boolean observedList = collection.getKind() == Observation.Sequence.Kind.LIST;
        boolean observedSet = collection.getKind() == Observation.Sequence.Kind.SET;
        List<String> elements = collection.isComplete() ? simple(collection.getElements()) : null;
        if (elements != null) {
            String list = "java.util.Arrays.asList(" + listArguments(elements) + ")";
            if (observedSet) {
                String actual = type.getKind() == TypeShape.Kind.SET ? expression
                        : "new java.util.HashSet<>(" + expression + ")";
                assertion("assertEquals", "new java.util.HashSet<>(" + list + ")", actual);
            } else {
                String actual = type.getKind() == TypeShape.Kind.LIST && observedList ? expression
                        : "new java.util.ArrayList<>(" + expression + ")";
                assertion("assertEquals", list, actual);
            }
            return;
        }
        assertion("assertEquals", String.valueOf(collection.getSize()), expression + ".size()");
        if (type.getKind() == TypeShape.Kind.LIST && observedList) {
            TypeShape element = elementOf(type);
            for (int i = 0; i < collection.getElements().size(); i++) {
                write(expression + ".get(" + i + ")", element, collection.getElements().get(i));
            }
        }
        // Other collections have no index to reach an element by, so only their size is checked.
    }

    private void map(String expression, TypeShape type, Observation.MapOf map) {
        if (type.getKind() != TypeShape.Kind.MAP) {
            assertion("assertNotNull", expression);
            return;
        }
        if (map.getSize() == 0) {
            assertion("assertTrue", expression + ".isEmpty()");
            return;
        }
        assertion("assertEquals", String.valueOf(map.getSize()), expression + ".size()");
        TypeShape valueType = type.getMapValueType() != null ? type.getMapValueType() : ObjectShape.INSTANCE;
        for (Observation.Entry entry : map.getEntries()) {
            String key = simple(entry.getKey());
            if (key != null) {
                write(expression + ".get(" + key + ")", valueType, entry.getValue());
            }
        }
    }

    private void bean(String expression, TypeShape type, Observation.Bean bean) {
        boolean checked = false;
        if (type.isAccessible()) {
            for (TypeShape.Accessor accessor : type.getAccessors()) {
                Observation property = bean.getProperties().get(accessor.methodName());
                if (property != null) {
                    write(expression + "." + accessor.methodName() + "()", accessor.type(), property);
                    checked = true;
                }
            }
        }
        if (!checked) {
            assertion("assertNotNull", expression);
        }
    }

    private void unrecorded(String expression, TypeShape type, Observation.Unrecorded unrecorded) {
        if (unrecorded.getReason() == Observation.Unrecorded.Reason.THREW) {
            statements.add("// " + context.comments().accessorThrew(expression, String.valueOf(unrecorded.getDetail())));
        } else if (unrecorded.getReason() == Observation.Unrecorded.Reason.LIMIT_REACHED) {
            if (count < MAX_ASSERTIONS) {
                statements.add("// " + context.comments().moreValuesNotChecked());
                count = MAX_ASSERTIONS + 1;
            }
        } else if (type.getKind() != TypeShape.Kind.PRIMITIVE) {
            assertion("assertNotNull", expression);
        }
    }

    /** The elements as expressions of exactly their recorded class, or null if one of them can't be written so. */
    private @Nullable List<String> simple(List<Observation> values) {
        List<String> expressions = new ArrayList<>(values.size());
        for (Observation value : values) {
            String expression = simple(value);
            if (expression == null) {
                return null;
            }
            expressions.add(expression);
        }
        return expressions;
    }

    private @Nullable String simple(Observation value) {
        if (value instanceof Observation.Null) {
            return "null";
        }
        if (value instanceof Observation.Literal) {
            return JavaLiterals.of(((Observation.Literal) value).getValue());
        }
        if (value instanceof Observation.EnumConstant) {
            Observation.EnumConstant constant = (Observation.EnumConstant) value;
            String enumName = context.referableName(constant.getEnumClassBinaryName());
            return enumName != null ? enumName + "." + constant.getConstantName() : null;
        }
        return null;
    }

    /** Arrays.asList(null) would pass a null array rather than one null element. */
    private static String listArguments(List<String> elements) {
        return elements.size() == 1 && elements.get(0).equals("null") ? "(java.lang.Object) null"
                : String.join(", ", elements);
    }

    /** Whether an array of the type can be written out as {@code new T[]{...}} from simple values. */
    private static boolean isPlainComponent(TypeShape component) {
        return switch (component.getKind()) {
            case PRIMITIVE, BOXED, STRING, BIG_NUMBER, ENUM -> component.isAccessible();
            default -> false;
        };
    }

    private static TypeShape elementOf(TypeShape type) {
        return type.getElementType() != null ? type.getElementType() : ObjectShape.INSTANCE;
    }

    private void assertion(String method, String... arguments) {
        statements.add(method + "(" + String.join(", ", arguments) + ");");
        assertions.add(method);
        count++;
    }

    /** What is known of an element or value whose type isn't: it is some object. */
    static final class ObjectShape implements TypeShape {

        static final ObjectShape INSTANCE = new ObjectShape();

        @Override
        public Kind getKind() {
            return Kind.OTHER;
        }

        @Override
        public String getText() {
            return "java.lang.Object";
        }

        @Override
        public boolean isAccessible() {
            return true;
        }

        @Override
        public @Nullable TypeShape getElementType() {
            return null;
        }

        @Override
        public @Nullable TypeShape getMapValueType() {
            return null;
        }

        @Override
        public List<Accessor> getAccessors() {
            return List.of();
        }
    }
}
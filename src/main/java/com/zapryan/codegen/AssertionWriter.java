package com.zapryan.codegen;

import com.zapryan.bootstrap.Observation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes assertions that check a value against what the run recorded for it. The run recorded only what the test can
 * read through the value's declared type, so each recorded value is checked as it is: literals with assertEquals,
 * objects through their getters, lists and arrays element by element, sets and maps by size and contents, and
 * values with no way to read them only for not being null.
 */
final class AssertionWriter {

    static final int MAX_ASSERTIONS = 50;
    static final String MORE_VALUES_COMMENT = "// More values were returned than are checked here";

    private final TestFramework framework;
    private final List<String> statements = new ArrayList<>();
    private final Set<String> assertions = new TreeSet<>();
    private int count;

    AssertionWriter(TestFramework framework) {
        this.framework = framework;
    }

    List<String> getStatements() {
        return statements;
    }

    /** The assertion methods used, to import statically from the framework's assertions class. */
    Set<String> getAssertions() {
        return assertions;
    }

    void write(String expression, Observation observed) {
        if (count > MAX_ASSERTIONS) {
            return;
        }
        if (count == MAX_ASSERTIONS) {
            statements.add(MORE_VALUES_COMMENT);
            count++;
            return;
        }
        if (observed instanceof Observation.Null) {
            assertion("assertNull", expression);
        } else if (observed instanceof Observation.Literal) {
            literal(expression, (Observation.Literal) observed);
        } else if (observed instanceof Observation.EnumConstant) {
            enumConstant(expression, (Observation.EnumConstant) observed);
        } else if (observed instanceof Observation.Text) {
            assertion("assertEquals", JavaLiterals.quote(((Observation.Text) observed).getText()),
                    expression + ".toString()");
        } else if (observed instanceof Observation.Sequence) {
            Observation.Sequence sequence = (Observation.Sequence) observed;
            if (sequence.getKind() == Observation.Sequence.Kind.ARRAY) {
                array(expression, sequence);
            } else {
                collection(expression, sequence);
            }
        } else if (observed instanceof Observation.MapOf) {
            map(expression, (Observation.MapOf) observed);
        } else if (observed instanceof Observation.Bean) {
            ((Observation.Bean) observed).getProperties()
                    .forEach((getter, property) -> write(expression + "." + getter + "()", property));
        } else if (observed instanceof Observation.Unrecorded) {
            unrecorded(expression, (Observation.Unrecorded) observed);
        }
    }

    private void literal(String expression, Observation.Literal literal) {
        Object value = literal.getValue();
        if (value instanceof Boolean && literal.isPrimitive()) {
            assertion((Boolean) value ? "assertTrue" : "assertFalse", expression);
        } else if (!literal.isPrimitive()) {
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
        if (constant.getEnumName() != null) {
            assertion("assertEquals", constant.getEnumName() + "." + constant.getConstantName(), expression);
        } else {
            assertion("assertEquals", JavaLiterals.quote(constant.getConstantName()),
                    "((java.lang.Enum<?>) " + expression + ").name()");
        }
    }

    private void array(String expression, Observation.Sequence array) {
        if (array.getSize() == 0) {
            assertion("assertEquals", "0", expression + ".length");
            return;
        }
        String elementType = array.getLiteralElementType();
        List<String> elements = array.isComplete() && elementType != null ? simple(array.getElements()) : null;
        if (elements != null) {
            String expected = "new " + elementType + "[]{" + String.join(", ", elements) + "}";
            String delta = elementType.equals("double") ? "0.0" : elementType.equals("float") ? "0.0f" : null;
            if (delta != null && framework.needsFloatingPointDelta()) {
                assertion("assertArrayEquals", expected, expression, delta);
            } else {
                assertion("assertArrayEquals", expected, expression);
            }
            return;
        }
        assertion("assertEquals", String.valueOf(array.getSize()), expression + ".length");
        for (int i = 0; i < array.getElements().size(); i++) {
            write(expression + "[" + i + "]", array.getElements().get(i));
        }
    }

    private void collection(String expression, Observation.Sequence collection) {
        if (collection.getSize() == 0) {
            assertion("assertTrue", expression + ".isEmpty()");
            return;
        }
        Observation.Sequence.Kind kind = collection.getKind();
        List<String> elements = collection.isComplete() ? simple(collection.getElements()) : null;
        if (elements != null) {
            String list = "java.util.Arrays.asList(" + listArguments(elements) + ")";
            if (kind == Observation.Sequence.Kind.SET) {
                // Sets are equal to any set with the same elements, whatever its class.
                assertion("assertEquals", "new java.util.HashSet<>(" + list + ")", expression);
            } else if (kind == Observation.Sequence.Kind.LIST) {
                assertion("assertEquals", list, expression);
            } else {
                assertion("assertEquals", list, "new java.util.ArrayList<>(" + expression + ")");
            }
            return;
        }
        assertion("assertEquals", String.valueOf(collection.getSize()), expression + ".size()");
        if (kind == Observation.Sequence.Kind.LIST) {
            for (int i = 0; i < collection.getElements().size(); i++) {
                write(expression + ".get(" + i + ")", collection.getElements().get(i));
            }
        }
        // Other collections have no index to reach an element by, so only their size is checked.
    }

    private void map(String expression, Observation.MapOf map) {
        if (map.getSize() == 0) {
            assertion("assertTrue", expression + ".isEmpty()");
            return;
        }
        assertion("assertEquals", String.valueOf(map.getSize()), expression + ".size()");
        for (Observation.Entry entry : map.getEntries()) {
            String key = simple(entry.getKey());
            if (key != null) {
                write(expression + ".get(" + key + ")", entry.getValue());
            }
        }
    }

    private void unrecorded(String expression, Observation.Unrecorded unrecorded) {
        switch (unrecorded.getReason()) {
            case THREW -> statements.add("// " + expression + " threw " + unrecorded.getDetail()
                    + " when the values were recorded");
            case LIMIT_REACHED -> {
                if (count < MAX_ASSERTIONS) {
                    statements.add(MORE_VALUES_COMMENT);
                    count = MAX_ASSERTIONS + 1;
                }
            }
            default -> assertion("assertNotNull", expression);
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

    private static @Nullable String simple(Observation value) {
        if (value instanceof Observation.Null) {
            return "null";
        }
        if (value instanceof Observation.Literal) {
            return JavaLiterals.of(((Observation.Literal) value).getValue());
        }
        if (value instanceof Observation.EnumConstant) {
            Observation.EnumConstant constant = (Observation.EnumConstant) value;
            return constant.getEnumName() != null ? constant.getEnumName() + "." + constant.getConstantName() : null;
        }
        return null;
    }

    /** Arrays.asList(null) would pass a null array rather than one null element. */
    private static String listArguments(List<String> elements) {
        return elements.size() == 1 && elements.get(0).equals("null") ? "(java.lang.Object) null"
                : String.join(", ", elements);
    }

    private void assertion(String method, String... arguments) {
        statements.add(method + "(" + String.join(", ", arguments) + ");");
        assertions.add(method);
        count++;
    }
}
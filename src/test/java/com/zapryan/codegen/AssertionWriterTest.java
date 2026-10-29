package com.zapryan.codegen;

import com.zapryan.bootstrap.Observation;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssertionWriterTest {

    @Test
    public void writesEachLiteralTheWayItsDeclaredTypeCanBeCompared() {
        assertEquals(List.of("assertEquals(5, result);"), assertions(primitive(5)));
        assertEquals(List.of("assertEquals(5L, result);"), assertions(primitive(5L)));
        assertEquals(List.of("assertEquals(java.lang.Integer.valueOf(5), result);"), assertions(literal(5)));
        assertEquals(List.of("assertEquals(\"text\", result);"), assertions(literal("text")));
        assertEquals(List.of("assertEquals(new java.math.BigDecimal(\"19.90\"), result);"),
                assertions(literal(new BigDecimal("19.90"))));
        assertEquals(List.of("assertTrue(result);"), assertions(primitive(true)));
        assertEquals(List.of("assertEquals(java.lang.Boolean.FALSE, result);"), assertions(literal(false)));
        assertEquals(List.of("assertNull(result);"), assertions(new Observation.Null()));
    }

    @Test
    public void comparesFloatingPointNumbersWithAToleranceOnlyInJUnit4() {
        assertEquals(List.of("assertEquals(1.5, result);"), assertions(primitive(1.5)));
        assertEquals(List.of("assertEquals(1.5, result, 0.0);"), assertions(TestFramework.JUNIT4, primitive(1.5)));
        assertEquals(List.of("assertEquals(1.5f, result, 0.0f);"), assertions(TestFramework.JUNIT4, primitive(1.5f)));
    }

    @Test
    public void readsObjectsThroughTheirRecordedGetters() {
        List<String> assertions = assertions(bean(
                "getId", primitive(7L),
                "getCustomer", bean("getName", literal("Ann")),
                "getNote", new Observation.Null()));

        assertEquals(List.of(
                "assertEquals(7L, result.getId());",
                "assertEquals(\"Ann\", result.getCustomer().getName());",
                "assertNull(result.getNote());"), assertions);
    }

    @Test
    public void checksOnlyThatAValueIsThereWhenTheTestCantReadIt() {
        assertEquals(List.of("assertNotNull(result);"),
                assertions(new Observation.Unrecorded(Observation.Unrecorded.Reason.OPAQUE, null)));
    }

    @Test
    public void comparesListsOfSimpleValuesWholeAndOthersElementByElement() {
        assertEquals(List.of("assertEquals(java.util.Arrays.asList(\"a\", null), result);"),
                assertions(list(literal("a"), new Observation.Null())));
        assertEquals(List.of("assertEquals(1, result.size());", "assertEquals(\"Ann\", result.get(0).getName());"),
                assertions(list(bean("getName", literal("Ann")))));
    }

    @Test
    public void comparesSetsAsSetsAndOtherCollectionsInOrder() {
        assertEquals(List.of("assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(3, 1)), result);"),
                assertions(sequence(Observation.Sequence.Kind.SET, literal(3), literal(1))));
        assertEquals(List.of("assertEquals(java.util.Arrays.asList(\"a\"), new java.util.ArrayList<>(result));"),
                assertions(sequence(Observation.Sequence.Kind.OTHER_COLLECTION, literal("a"))));
        assertEquals(List.of("assertEquals(1, result.size());"),
                assertions(sequence(Observation.Sequence.Kind.OTHER_COLLECTION, bean("getName", literal("Ann")))));
        assertEquals(List.of("assertEquals(java.util.Arrays.asList((java.lang.Object) null), result);"),
                assertions(list(new Observation.Null())));
        assertEquals(List.of("assertTrue(result.isEmpty());"), assertions(list()));
    }

    @Test
    public void comparesArraysOfLiteralsWholeAndOthersElementByElement() {
        assertEquals(List.of("assertArrayEquals(new int[]{4, 2}, result);"),
                assertions(array("int", primitive(4), primitive(2))));
        assertEquals(List.of("assertArrayEquals(new double[]{0.5}, result, 0.0);"),
                assertions(TestFramework.JUNIT4, array("double", primitive(0.5))));
        assertEquals(List.of("assertEquals(1, result.length);", "assertEquals(\"Ann\", result[0].getName());"),
                assertions(array(null, bean("getName", literal("Ann")))));
        assertEquals(List.of("assertEquals(0, result.length);"), assertions(array("java.lang.String")));
    }

    @Test
    public void readsMapsBySimpleKeys() {
        Observation stock = new Observation.MapOf(2, List.of(
                new Observation.Entry(literal("pen"), literal(2)),
                new Observation.Entry(bean("getCode", literal(1)), literal(3))));

        assertEquals(List.of("assertEquals(2, result.size());",
                "assertEquals(java.lang.Integer.valueOf(2), result.get(\"pen\"));"), assertions(stock));
    }

    @Test
    public void namesEnumConstantsByTheirEnumWhenTheTestCanSeeIt() {
        assertEquals(List.of("assertEquals(com.acme.Order.Status.OPEN, result);"),
                assertions(new Observation.EnumConstant("com.acme.Order.Status", "OPEN")));
        assertEquals(List.of("assertEquals(\"OPEN\", ((java.lang.Enum<?>) result).name());"),
                assertions(new Observation.EnumConstant(null, "OPEN")));
    }

    @Test
    public void comparesJdkValuesByTheirText() {
        assertEquals(List.of("assertEquals(\"2024-01-02\", result.toString());"),
                assertions(new Observation.Text("java.time.LocalDate", "2024-01-02")));
    }

    @Test
    public void notesValuesThatWerentRecorded() {
        assertEquals(List.of("// result.getBroken() threw java.lang.IllegalStateException when the values were recorded",
                        "assertNotNull(result.getParent());"),
                assertions(bean(
                        "getBroken", new Observation.Unrecorded(Observation.Unrecorded.Reason.THREW,
                                "java.lang.IllegalStateException"),
                        "getParent", new Observation.Unrecorded(Observation.Unrecorded.Reason.CYCLE, null))));
    }

    @Test
    public void stopsAfterTheMostAssertionsAndSaysSo() {
        List<Observation> numbers = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            numbers.add(bean("getValue", primitive(i)));
        }

        List<String> assertions = assertions(list(numbers.toArray(new Observation[0])));

        assertEquals(AssertionWriter.MAX_ASSERTIONS + 1, assertions.size());
        assertEquals(AssertionWriter.MORE_VALUES_COMMENT, assertions.get(assertions.size() - 1));
        assertTrue(assertions.contains("assertEquals(48, result.get(48).getValue());"));
    }

    private static List<String> assertions(Observation observed) {
        return assertions(TestFramework.JUNIT5, observed);
    }

    private static List<String> assertions(TestFramework framework, Observation observed) {
        AssertionWriter writer = new AssertionWriter(framework);
        writer.write("result", observed);
        return writer.getStatements();
    }

    /** A value declared as a primitive. */
    private static Observation primitive(Serializable value) {
        return new Observation.Literal(value, true);
    }

    private static Observation literal(Serializable value) {
        return new Observation.Literal(value, false);
    }

    private static Observation list(Observation... elements) {
        return sequence(Observation.Sequence.Kind.LIST, elements);
    }

    private static Observation sequence(Observation.Sequence.Kind kind, Observation... elements) {
        return new Observation.Sequence(kind, elements.length, List.of(elements), null);
    }

    private static Observation array(String literalElementType, Observation... elements) {
        return new Observation.Sequence(Observation.Sequence.Kind.ARRAY, elements.length, List.of(elements),
                literalElementType);
    }

    /** A bean recorded with alternating getter names and values. */
    private static Observation bean(Object... gettersAndValues) {
        Map<String, Observation> properties = new LinkedHashMap<>();
        for (int i = 0; i < gettersAndValues.length; i += 2) {
            properties.put((String) gettersAndValues[i], (Observation) gettersAndValues[i + 1]);
        }
        return new Observation.Bean("com.acme.Recorded", properties);
    }
}
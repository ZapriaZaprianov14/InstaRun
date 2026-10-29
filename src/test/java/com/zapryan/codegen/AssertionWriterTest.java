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

    private final FakeContext context = new FakeContext();

    @Test
    public void writesEachLiteralTheWayItsTypeCanBeCompared() {
        assertEquals(List.of("assertEquals(5, result);"), assertions(FakeShape.primitive("int"), literal(5)));
        assertEquals(List.of("assertEquals(5L, result);"), assertions(FakeShape.primitive("long"), literal(5L)));
        assertEquals(List.of("assertEquals(java.lang.Integer.valueOf(5), result);"),
                assertions(FakeShape.boxed("Integer"), literal(5)));
        assertEquals(List.of("assertEquals(java.lang.Long.valueOf(5L), result);"),
                assertions(FakeShape.object("java.lang.Number"), literal(5L)));
        assertEquals(List.of("assertEquals(\"text\", result);"), assertions(FakeShape.string(), literal("text")));
        assertEquals(List.of("assertEquals(new java.math.BigDecimal(\"19.90\"), result);"),
                assertions(FakeShape.object("java.math.BigDecimal"), literal(new BigDecimal("19.90"))));
        assertEquals(List.of("assertTrue(result);"), assertions(FakeShape.primitive("boolean"), literal(true)));
        assertEquals(List.of("assertFalse(result);"), assertions(FakeShape.boxed("Boolean"), literal(false)));
        assertEquals(List.of("assertNull(result);"), assertions(FakeShape.string(), new Observation.Null()));
    }

    @Test
    public void comparesFloatingPointNumbersWithAToleranceOnlyInJUnit4() {
        assertEquals(List.of("assertEquals(1.5, result);"), assertions(FakeShape.primitive("double"), literal(1.5)));
        assertEquals(List.of("assertEquals(1.5, result, 0.0);"),
                assertions(TestFramework.JUNIT4, FakeShape.primitive("double"), literal(1.5)));
        assertEquals(List.of("assertEquals(1.5f, result, 0.0f);"),
                assertions(TestFramework.JUNIT4, FakeShape.primitive("float"), literal(1.5f)));
    }

    @Test
    public void readsObjectsThroughTheGettersOfTheirDeclaredType() {
        FakeShape customer = FakeShape.object("com.acme.Customer").with("getName", FakeShape.string());
        FakeShape order = FakeShape.object("com.acme.Order")
                .with("getId", FakeShape.primitive("long"))
                .with("getCustomer", customer)
                .with("getNote", FakeShape.string());

        List<String> assertions = assertions(order, bean(
                "getId", literal(7L),
                "getCustomer", bean("getName", literal("Ann")),
                "getNote", new Observation.Null(),
                "getInternal", literal("not on the declared type")));

        assertEquals(List.of(
                "assertEquals(7L, result.getId());",
                "assertEquals(\"Ann\", result.getCustomer().getName());",
                "assertNull(result.getNote());"), assertions);
    }

    @Test
    public void checksOnlyThatAnObjectIsThereWhenItsTypeCantBeRead() {
        assertEquals(List.of("assertNotNull(result);"),
                assertions(FakeShape.object("com.acme.Hidden").inaccessible().with("getName", FakeShape.string()),
                        bean("getName", literal("Ann"))));
        assertEquals(List.of("assertNotNull(result);"),
                assertions(FakeShape.object("java.lang.Object"), bean("getName", literal("Ann"))));
    }

    @Test
    public void comparesListsOfSimpleValuesWholeAndOthersElementByElement() {
        assertEquals(List.of("assertEquals(java.util.Arrays.asList(\"a\", null), result);"),
                assertions(FakeShape.list(FakeShape.string()), list(literal("a"), new Observation.Null())));

        FakeShape customer = FakeShape.object("com.acme.Customer").with("getName", FakeShape.string());
        assertEquals(List.of("assertEquals(1, result.size());", "assertEquals(\"Ann\", result.get(0).getName());"),
                assertions(FakeShape.list(customer), list(bean("getName", literal("Ann")))));
    }

    @Test
    public void comparesSetsAsSetsAndOtherCollectionsInOrder() {
        assertEquals(List.of("assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(3, 1)), result);"),
                assertions(FakeShape.set(FakeShape.boxed("Integer")), sequence(Observation.Sequence.Kind.SET,
                        literal(3), literal(1))));
        assertEquals(List.of("assertEquals(java.util.Arrays.asList(\"a\"), new java.util.ArrayList<>(result));"),
                assertions(FakeShape.collection(FakeShape.string()), sequence(Observation.Sequence.Kind.OTHER_COLLECTION,
                        literal("a"))));
        assertEquals(List.of("assertEquals(java.util.Arrays.asList((java.lang.Object) null), result);"),
                assertions(FakeShape.list(FakeShape.string()), list(new Observation.Null())));
        assertEquals(List.of("assertTrue(result.isEmpty());"), assertions(FakeShape.list(FakeShape.string()), list()));
    }

    @Test
    public void comparesArraysOfSimpleValuesWhole() {
        assertEquals(List.of("assertArrayEquals(new int[]{4, 2}, result);"),
                assertions(FakeShape.array(FakeShape.primitive("int")), sequence(Observation.Sequence.Kind.ARRAY,
                        literal(4), literal(2))));
        assertEquals(List.of("assertArrayEquals(new double[]{0.5}, result, 0.0);"),
                assertions(TestFramework.JUNIT4, FakeShape.array(FakeShape.primitive("double")),
                        sequence(Observation.Sequence.Kind.ARRAY, literal(0.5))));
        assertEquals(List.of("assertEquals(0, result.length);"),
                assertions(FakeShape.array(FakeShape.string()), sequence(Observation.Sequence.Kind.ARRAY)));
    }

    @Test
    public void readsMapsBySimpleKeys() {
        Observation stock = new Observation.MapOf(2, List.of(
                new Observation.Entry(literal("pen"), literal(2)),
                new Observation.Entry(bean("getCode", literal(1)), literal(3))));

        assertEquals(List.of("assertEquals(2, result.size());", "assertEquals(java.lang.Integer.valueOf(2), result.get(\"pen\"));"),
                assertions(FakeShape.map(FakeShape.object("com.acme.Key"), FakeShape.boxed("Integer")), stock));
    }

    @Test
    public void namesEnumConstantsByTheirEnumWhenTheTestCanReferToIt() {
        Observation open = new Observation.EnumConstant("com.acme.Order$Status", "OPEN");

        assertEquals(List.of("assertEquals(com.acme.Order.Status.OPEN, result);"),
                assertions(FakeShape.enumType("com.acme.Order.Status"), open));
        context.hide("com.acme.Order$Status");
        assertEquals(List.of("assertEquals(\"OPEN\", ((java.lang.Enum<?>) result).name());"),
                assertions(FakeShape.object("java.lang.Object"), open));
    }

    @Test
    public void comparesJdkValuesByTheirText() {
        assertEquals(List.of("assertEquals(\"2024-01-02\", result.toString());"),
                assertions(FakeShape.object("java.time.LocalDate"), new Observation.Text("java.time.LocalDate", "2024-01-02")));
    }

    @Test
    public void notesValuesThatWerentRecorded() {
        FakeShape order = FakeShape.object("com.acme.Order")
                .with("getBroken", FakeShape.primitive("int"))
                .with("getParent", FakeShape.object("com.acme.Order"));

        assertEquals(List.of("// result.getBroken() threw java.lang.IllegalStateException", "assertNotNull(result.getParent());"),
                assertions(order, bean(
                        "getBroken", new Observation.Unrecorded(Observation.Unrecorded.Reason.THREW, "java.lang.IllegalStateException"),
                        "getParent", new Observation.Unrecorded(Observation.Unrecorded.Reason.CYCLE, null))));
    }

    @Test
    public void stopsAfterTheMostAssertionsAndSaysSo() {
        List<Observation> numbers = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            numbers.add(bean("getValue", literal(i)));
        }
        FakeShape number = FakeShape.object("com.acme.Number").with("getValue", FakeShape.primitive("int"));

        List<String> assertions = assertions(FakeShape.list(number), sequence(Observation.Sequence.Kind.LIST,
                numbers.toArray(new Observation[0])));

        assertEquals(AssertionWriter.MAX_ASSERTIONS + 1, assertions.size());
        assertEquals("// More values", assertions.get(assertions.size() - 1));
        assertTrue(assertions.contains("assertEquals(48, result.get(48).getValue());"));
    }

    private List<String> assertions(TypeShape type, Observation observed) {
        return assertions(TestFramework.JUNIT5, type, observed);
    }

    private List<String> assertions(TestFramework framework, TypeShape type, Observation observed) {
        AssertionWriter writer = new AssertionWriter(framework, context);
        writer.write("result", type, observed);
        return writer.getStatements();
    }

    private static Observation literal(Serializable value) {
        return new Observation.Literal(value);
    }

    private static Observation list(Observation... elements) {
        return sequence(Observation.Sequence.Kind.LIST, elements);
    }

    private static Observation sequence(Observation.Sequence.Kind kind, Observation... elements) {
        return new Observation.Sequence(kind, elements.length, List.of(elements));
    }

    /** A bean recorded with alternating accessor names and values. */
    private static Observation bean(Object... accessorsAndValues) {
        Map<String, Observation> properties = new LinkedHashMap<>();
        for (int i = 0; i < accessorsAndValues.length; i += 2) {
            properties.put((String) accessorsAndValues[i], (Observation) accessorsAndValues[i + 1]);
        }
        return new Observation.Bean("com.acme.Recorded", properties);
    }
}
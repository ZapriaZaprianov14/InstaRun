package com.zapryan.bootstrap;

import com.zapryan.bootstrap.fixtures.ResultTargets;
import org.junit.Test;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ObserverTest {

    private static final String RESULTS = "com.zapryan.bootstrap.fixtures.ResultTargets";
    private static final String TEST_PACKAGE = "com.zapryan.bootstrap.fixtures";

    @Test
    public void recordsLiteralsAndEnumConstantsAsTheyAre() {
        assertEquals(new Observation.Literal(5, true), observe(5, int.class));
        assertEquals(literal(5), observe(5, Integer.class));
        assertEquals(literal('c'), observe('c', Object.class));
        assertEquals(literal(new BigDecimal("1.50")), observe(new BigDecimal("1.50"), BigDecimal.class));
        assertEquals(new Observation.Null(), observe(null, String.class));
        // A constant with a body has a class of its own; it is recorded by the enum's class.
        assertEquals(new Observation.EnumConstant(RESULTS + ".Status", "CLOSED"),
                observe(ResultTargets.Status.CLOSED, ResultTargets.Status.class));
    }

    @Test
    public void recordsAnObjectThroughItsGettersSortedByName() {
        Map<String, Observation> order = order();

        assertEquals(Arrays.asList("getBroken", "getCodes", "getCustomer", "getId", "getLines", "getLock", "getNote",
                        "getPlaced", "getScores", "getStatus", "getStock", "getTotal", "isGift", "isPaid"),
                new ArrayList<>(order.keySet()));
        assertEquals(new Observation.Literal(7L, true), order.get("getId"));
        assertEquals(new Observation.Literal(true, true), order.get("isPaid"));
        assertEquals(literal(false), order.get("isGift"));
        assertEquals(new Observation.Null(), order.get("getNote"));
        assertEquals(literal(new BigDecimal("19.90")), order.get("getTotal"));
        assertEquals(literal("Ann"), bean(order.get("getCustomer"), RESULTS + "$Customer").get("getName"));
    }

    @Test
    public void recordsArraysCollectionsAndMapsElementByElement() {
        Map<String, Observation> order = order();

        assertEquals(sequence(Observation.Sequence.Kind.LIST, literal("pen"), literal("ink")), order.get("getLines"));
        assertEquals(sequence(Observation.Sequence.Kind.SET, literal(3), literal(1)), order.get("getCodes"));
        assertEquals(new Observation.Sequence(Observation.Sequence.Kind.ARRAY, 2,
                        Arrays.<Observation>asList(new Observation.Literal(4, true), new Observation.Literal(2, true)), "int"),
                order.get("getScores"));
        assertEquals(new Observation.MapOf(1, Collections.singletonList(new Observation.Entry(literal("pen"), literal(2)))),
                order.get("getStock"));
    }

    @Test
    public void readsAListDeclaredAsAnotherCollectionInOrder() {
        assertEquals(sequence(Observation.Sequence.Kind.OTHER_COLLECTION, literal(0), literal(1)),
                observe(ResultTargets.numbers(2), Collection.class));
    }

    @Test
    public void recordsJdkObjectsByTheirToStringOrNotAtAll() {
        Map<String, Observation> order = order();

        assertEquals(new Observation.Text("java.time.LocalDate", "2024-01-02"), order.get("getPlaced"));
        assertEquals(unrecorded(Observation.Unrecorded.Reason.OPAQUE, null), order.get("getLock"));
    }

    @Test
    public void recordsWhichGetterThrewAndCarriesOn() {
        assertEquals(unrecorded(Observation.Unrecorded.Reason.THREW, "java.lang.IllegalStateException"),
                order().get("getBroken"));
    }

    @Test
    public void readsOnlyTheGettersOfTheDeclaredType() {
        Map<String, Observation> shape = bean(observe(ResultTargets.shape(), ResultTargets.Shape.class), RESULTS + "$Circle");

        assertEquals(Collections.singletonMap("getArea", new Observation.Literal(3.5, true)), shape);
    }

    @Test
    public void readsGettersOfAGenericClassWithTheDeclaredTypeArguments() {
        Map<String, Observation> box = bean(observe(ResultTargets.boxed(), returnType("boxed")), RESULTS + "$Box");

        assertEquals(literal("Ann"), bean(box.get("getValue"), RESULTS + "$Customer").get("getName"));
    }

    @Test
    public void recordsNothingTheTestHasNoWayToRead() {
        Observation opaque = unrecorded(Observation.Unrecorded.Reason.OPAQUE, null);

        assertEquals(opaque, observe(ResultTargets.order(), Object.class));
        assertEquals(opaque, observe(ResultTargets.numbers(1), Object.class));
        // Circle is private, so the test can't call its getters.
        assertEquals(opaque, ((Observation.Sequence) observe(ResultTargets.circles(), returnType("circles")))
                .getElements().get(0));
        assertEquals(new Observation.EnumConstant(null, "HIDDEN"), observe(ResultTargets.secret(), Object.class));
    }

    @Test
    public void declaresTheResultAsTheReturnTypeWhenTheTestCanWriteIt() throws Exception {
        assertEquals("java.util.List<" + RESULTS + ".Shape>", declarable(returnType("shapes")));
        assertEquals(RESULTS + ".Box<" + RESULTS + ".Customer>", declarable(returnType("boxed")));
        assertEquals("int[]", declarable(ResultTargets.Order.class.getMethod("getScores").getGenericReturnType()));
        // Erased when it names a class the test can't see or a type variable, and Object if even that won't do.
        assertEquals("java.util.List", declarable(returnType("circles")));
        assertEquals("java.lang.Object", declarable(returnType("anything")));
        assertEquals("java.lang.Object", declarable(ResultTargets.shape().getClass()));
    }

    @Test
    public void findsTheTypeArgumentsACollectionGivesThroughItsSupertypes() {
        assertEquals(String.class, Observer.typeArgument(ResultTargets.Names.class, Collection.class, 0));
        assertEquals("java.util.List<java.util.List<java.lang.Integer>>",
                Observer.typeText(Observer.typeArgument(returnType("cube", int.class), Collection.class, 0)));
        assertEquals(Object.class, Observer.rawOf(Observer.typeArgument(List.class, Collection.class, 0)));
    }

    @Test
    public void namesAnExceptionByTheNearestClassTheTestCanSee() throws Exception {
        try {
            ResultTargets.refuse();
            fail("refuse() should throw");
        } catch (IllegalArgumentException e) {
            assertEquals(new Outcome.Threw("java.lang.IllegalArgumentException", null),
                    Outcome.Threw.of(ResultTargets.class.getMethod("refuse"), e));
        }
    }

    @Test
    public void stopsAtAnObjectThatContainsItself() {
        assertEquals(unrecorded(Observation.Unrecorded.Reason.CYCLE, null),
                bean(observe(ResultTargets.loop(), ResultTargets.Node.class), RESULTS + "$Node").get("getNext"));
    }

    @Test
    public void stopsLookingIntoObjectsNestedTooDeep() {
        Map<String, Observation> first = bean(observe(ResultTargets.deep(), ResultTargets.Level.class), RESULTS + "$Level");
        Map<String, Observation> second = bean(first.get("getInner"), RESULTS + "$Level");
        Map<String, Observation> third = bean(second.get("getInner"), RESULTS + "$Level");

        assertEquals(sequence(Observation.Sequence.Kind.LIST), second.get("getEmpty"));
        assertEquals(unrecorded(Observation.Unrecorded.Reason.TOO_DEEP, null), third.get("getInner"));
        assertEquals(unrecorded(Observation.Unrecorded.Reason.TOO_DEEP, null), third.get("getEmpty"));
    }

    @Test
    public void recordsTheFirstElementsOfALargeCollectionAndItsSize() {
        Observation.Sequence numbers = (Observation.Sequence) observe(ResultTargets.numbers(25), List.class);

        assertEquals(25, numbers.getSize());
        assertEquals(Observer.MAX_ELEMENTS, numbers.getElements().size());
        assertFalse(numbers.isComplete());
    }

    @Test
    public void stopsAfterTheMostValuesItRecords() {
        Observation.Sequence planes = (Observation.Sequence) observe(ResultTargets.cube(Observer.MAX_ELEMENTS),
                returnType("cube", int.class));

        assertTrue(planes.getElements().get(0) instanceof Observation.Sequence);
        assertEquals(unrecorded(Observation.Unrecorded.Reason.LIMIT_REACHED, null),
                planes.getElements().get(Observer.MAX_ELEMENTS - 1));
    }

    @Test
    public void treatsOnlyBooleanIsMethodsAsGetters() throws Exception {
        assertTrue(Observer.isGetter(ResultTargets.Order.class.getMethod("isGift")));
        assertFalse(Observer.isGetter(ResultTargets.Order.class.getMethod("compute")));
        assertFalse(Observer.isGetter(ResultTargets.Order.class.getMethod("getStatic")));
        assertFalse(Observer.isGetter(ResultTargets.Order.class.getMethod("getWithArgument", int.class)));
        assertFalse(Observer.isGetter(Object.class.getMethod("getClass")));
    }

    private static Observation observe(Object value, Type declared) {
        return new Observer(TEST_PACKAGE).observe(value, declared);
    }

    private static String declarable(Type type) {
        return Observer.typeText(new Observer(TEST_PACKAGE).declarable(type));
    }

    private static Type returnType(String methodName, Class<?>... parameterTypes) {
        try {
            return ResultTargets.class.getMethod(methodName, parameterTypes).getGenericReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Observation> order() {
        return bean(observe(ResultTargets.order(), ResultTargets.Order.class), RESULTS + "$Order");
    }

    private static Map<String, Observation> bean(Observation observation, String className) {
        Observation.Bean bean = (Observation.Bean) observation;
        assertEquals(className, bean.getClassBinaryName());
        return bean.getProperties();
    }

    private static Observation literal(java.io.Serializable value) {
        return new Observation.Literal(value, false);
    }

    private static Observation sequence(Observation.Sequence.Kind kind, Observation... elements) {
        return new Observation.Sequence(kind, elements.length, Arrays.asList(elements), null);
    }

    private static Observation unrecorded(Observation.Unrecorded.Reason reason, String detail) {
        return new Observation.Unrecorded(reason, detail);
    }
}
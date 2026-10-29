package com.zapryan.bootstrap;

import com.zapryan.bootstrap.fixtures.ResultTargets;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObserverTest {

    private static final String RESULTS = "com.zapryan.bootstrap.fixtures.ResultTargets";

    @Test
    public void recordsLiteralsAndEnumConstantsAsTheyAre() {
        assertEquals(literal(5), observe(5));
        assertEquals(literal('c'), observe('c'));
        assertEquals(literal(new BigDecimal("1.50")), observe(new BigDecimal("1.50")));
        assertEquals(new Observation.Null(), observe(null));
        // A constant with a body has a class of its own; it is recorded by the enum's class.
        assertEquals(new Observation.EnumConstant(RESULTS + "$Status", "CLOSED"), observe(ResultTargets.Status.CLOSED));
    }

    @Test
    public void recordsAnObjectThroughItsGettersSortedByName() {
        Map<String, Observation> order = bean(observe(ResultTargets.order()), RESULTS + "$Order");

        assertEquals(Arrays.asList("getBroken", "getCodes", "getCustomer", "getId", "getLines", "getLock", "getNote",
                        "getPlaced", "getScores", "getStatus", "getStock", "getTotal", "isGift", "isPaid"),
                new ArrayList<>(order.keySet()));
        assertEquals(literal(7L), order.get("getId"));
        assertEquals(literal(true), order.get("isPaid"));
        assertEquals(literal(false), order.get("isGift"));
        assertEquals(new Observation.Null(), order.get("getNote"));
        assertEquals(literal(new BigDecimal("19.90")), order.get("getTotal"));
        assertEquals(literal("Ann"), bean(order.get("getCustomer"), RESULTS + "$Customer").get("getName"));
    }

    @Test
    public void recordsArraysCollectionsAndMapsElementByElement() {
        Map<String, Observation> order = bean(observe(ResultTargets.order()), RESULTS + "$Order");

        assertEquals(new Observation.Sequence(Observation.Sequence.Kind.LIST, 2, Arrays.asList(literal("pen"), literal("ink"))),
                order.get("getLines"));
        assertEquals(new Observation.Sequence(Observation.Sequence.Kind.SET, 2, Arrays.asList(literal(3), literal(1))),
                order.get("getCodes"));
        assertEquals(new Observation.Sequence(Observation.Sequence.Kind.ARRAY, 2, Arrays.asList(literal(4), literal(2))),
                order.get("getScores"));
        assertEquals(new Observation.MapOf(1, Collections.singletonList(new Observation.Entry(literal("pen"), literal(2)))),
                order.get("getStock"));
    }

    @Test
    public void recordsJdkObjectsByTheirToStringOrNotAtAll() {
        Map<String, Observation> order = bean(observe(ResultTargets.order()), RESULTS + "$Order");

        assertEquals(new Observation.Text("java.time.LocalDate", "2024-01-02"), order.get("getPlaced"));
        assertEquals(unrecorded(Observation.Unrecorded.Reason.OPAQUE, null), order.get("getLock"));
    }

    @Test
    public void recordsWhichGetterThrewAndCarriesOn() {
        Map<String, Observation> order = bean(observe(ResultTargets.order()), RESULTS + "$Order");

        assertEquals(unrecorded(Observation.Unrecorded.Reason.THREW, "java.lang.IllegalStateException"),
                order.get("getBroken"));
    }

    @Test
    public void readsGettersOfAPrivateClassThroughItsPublicInterface() {
        assertEquals(literal(3.5), bean(observe(ResultTargets.shape()), RESULTS + "$Circle").get("getArea"));
    }

    @Test
    public void stopsAtAnObjectThatContainsItself() {
        assertEquals(unrecorded(Observation.Unrecorded.Reason.CYCLE, null),
                bean(observe(ResultTargets.loop()), RESULTS + "$Node").get("getNext"));
    }

    @Test
    public void stopsLookingIntoObjectsNestedTooDeep() {
        Map<String, Observation> first = bean(observe(ResultTargets.deep()), RESULTS + "$Level");
        Map<String, Observation> second = bean(first.get("getInner"), RESULTS + "$Level");
        Map<String, Observation> third = bean(second.get("getInner"), RESULTS + "$Level");

        assertEquals(new Observation.Sequence(Observation.Sequence.Kind.LIST, 0, Collections.<Observation>emptyList()),
                second.get("getEmpty"));
        assertEquals(unrecorded(Observation.Unrecorded.Reason.TOO_DEEP, null), third.get("getInner"));
        assertEquals(unrecorded(Observation.Unrecorded.Reason.TOO_DEEP, null), third.get("getEmpty"));
    }

    @Test
    public void recordsTheFirstElementsOfALargeCollectionAndItsSize() {
        Observation.Sequence numbers = (Observation.Sequence) observe(ResultTargets.numbers(25));

        assertEquals(25, numbers.getSize());
        assertEquals(Observer.MAX_ELEMENTS, numbers.getElements().size());
        assertFalse(numbers.isComplete());
    }

    @Test
    public void stopsAfterTheMostValuesItRecords() {
        List<List<List<Integer>>> cube = new ArrayList<>();
        for (int i = 0; i < Observer.MAX_ELEMENTS; i++) {
            List<List<Integer>> plane = new ArrayList<>();
            for (int j = 0; j < Observer.MAX_ELEMENTS; j++) {
                plane.add(ResultTargets.numbers(Observer.MAX_ELEMENTS));
            }
            cube.add(plane);
        }

        Observation.Sequence planes = (Observation.Sequence) observe(cube);

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

    private static Observation observe(Object value) {
        return new Observer().observe(value);
    }

    private static Map<String, Observation> bean(Observation observation, String className) {
        Observation.Bean bean = (Observation.Bean) observation;
        assertEquals(className, bean.getClassBinaryName());
        return bean.getProperties();
    }

    private static Observation literal(java.io.Serializable value) {
        return new Observation.Literal(value);
    }

    private static Observation unrecorded(Observation.Unrecorded.Reason reason, String detail) {
        return new Observation.Unrecorded(reason, detail);
    }
}
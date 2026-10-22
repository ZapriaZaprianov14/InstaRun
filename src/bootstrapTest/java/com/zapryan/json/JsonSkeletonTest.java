package com.zapryan.json;

import com.zapryan.reflection.TypeDescriptor;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.zapryan.json.TestJson.lines;
import static com.zapryan.reflection.TestTypes.BIG_DECIMAL;
import static com.zapryan.reflection.TestTypes.BOOLEAN;
import static com.zapryan.reflection.TestTypes.CHAR;
import static com.zapryan.reflection.TestTypes.INT;
import static com.zapryan.reflection.TestTypes.STRING;
import static com.zapryan.reflection.TestTypes.arrayOf;
import static com.zapryan.reflection.TestTypes.bean;
import static com.zapryan.reflection.TestTypes.constructor;
import static com.zapryan.reflection.TestTypes.enumOf;
import static com.zapryan.reflection.TestTypes.listOf;
import static com.zapryan.reflection.TestTypes.parameter;
import static com.zapryan.reflection.TestTypes.setter;
import static com.zapryan.reflection.TestTypes.unsupported;
import static org.junit.Assert.assertEquals;

public class JsonSkeletonTest {

    @Test
    public void usesAPlaceholderOfEachKindOfLiteral() {
        TypeDescriptor.BeanType sample = bean("Sample");
        sample.define(Collections.singletonList(constructor()), Arrays.asList(
                setter(sample, "text", STRING), setter(sample, "letter", CHAR), setter(sample, "flag", BOOLEAN),
                setter(sample, "count", INT), setter(sample, "price", BIG_DECIMAL),
                setter(sample, "status", enumOf("Status", "ACTIVE", "RETIRED"))));
        settle(sample);

        assertEquals(lines(
                "{",
                "  \"text\": \"string\",",
                "  \"letter\": \"a\",",
                "  \"flag\": true,",
                "  \"count\": 0,",
                "  \"price\": 0.0,",
                "  \"status\": \"ACTIVE\"",
                "}"), skeletonOf(sample));
    }

    @Test
    public void listsConstructorParametersFirstAndLeavesOutWhatCannotBeEntered() {
        TypeDescriptor.BeanType person = bean("Person");
        person.define(Collections.singletonList(constructor(parameter("name", STRING), parameter("age", INT))),
                Arrays.asList(setter(person, "email", STRING), setter(person, "meta", unsupported("Map<String, Object>"))));
        settle(person);

        assertEquals(lines(
                "{",
                "  \"name\": \"string\",",
                "  \"age\": 0,",
                "  \"email\": \"string\"",
                "}"), skeletonOf(person));
    }

    @Test
    public void givesListsAndArraysOneExampleElement() {
        TypeDescriptor.BeanType address = bean("Address");
        address.define(Collections.singletonList(constructor()), Collections.singletonList(setter(address, "street", STRING)));
        settle(address);

        assertEquals(lines(
                "[",
                "  {",
                "    \"street\": \"string\"",
                "  }",
                "]"), skeletonOf(listOf(address)));
        assertEquals(lines(
                "[",
                "  0",
                "]"), skeletonOf(arrayOf(INT)));
    }

    @Test
    public void cutsABeanThatAppearsInsideItself() {
        TypeDescriptor.BeanType node = bean("Node");
        node.define(Collections.singletonList(constructor()), Arrays.asList(
                setter(node, "value", INT), setter(node, "next", node), setter(node, "children", listOf(node))));
        settle(node);

        assertEquals(lines(
                "{",
                "  \"value\": 0,",
                "  \"next\": null,",
                "  \"children\": []",
                "}"), skeletonOf(node));
    }

    @Test
    public void stopsNestingBeansAtTheDepthCap() {
        TypeDescriptor.BeanType first = bean("First");
        TypeDescriptor.BeanType second = bean("Second");
        TypeDescriptor.BeanType third = bean("Third");
        TypeDescriptor.BeanType fourth = bean("Fourth");
        first.define(Collections.singletonList(constructor()), Collections.singletonList(setter(first, "next", second)));
        second.define(Collections.singletonList(constructor()), Collections.singletonList(setter(second, "next", third)));
        third.define(Collections.singletonList(constructor()), Collections.singletonList(setter(third, "next", fourth)));
        fourth.define(Collections.singletonList(constructor()), Collections.singletonList(setter(fourth, "value", INT)));
        settle(first, second, third, fourth);

        assertEquals(lines(
                "{",
                "  \"next\": {",
                "    \"next\": {",
                "      \"next\": null",
                "    }",
                "  }",
                "}"), skeletonOf(first));
    }

    @Test
    public void everySkeletonBindsBackToItsType() throws Exception {
        TypeDescriptor.BeanType node = bean("Node");
        node.define(Collections.singletonList(constructor(parameter("value", INT))), Arrays.asList(
                setter(node, "next", node), setter(node, "tags", arrayOf(STRING)), setter(node, "children", listOf(node))));
        settle(node);

        JsonBinder.bind(JsonSkeleton.of(node), node);
        JsonBinder.bind(JsonSkeleton.of(listOf(node)), listOf(node));
    }

    private static String skeletonOf(TypeDescriptor type) {
        return JsonWriter.write(JsonSkeleton.of(type));
    }

    private static void settle(TypeDescriptor.BeanType... beans) {
        TypeDescriptor.settle(Arrays.asList(beans));
    }
}
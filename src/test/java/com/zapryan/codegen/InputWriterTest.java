package com.zapryan.codegen;

import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.ReceiverDescriptor;
import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.junit.Test;

import java.io.Serializable;
import java.util.List;

import static com.zapryan.reflection.TestTypes.INT;
import static com.zapryan.reflection.TestTypes.INTEGER;
import static com.zapryan.reflection.TestTypes.STRING;
import static com.zapryan.reflection.TestTypes.arrayOf;
import static com.zapryan.reflection.TestTypes.bean;
import static com.zapryan.reflection.TestTypes.constructor;
import static com.zapryan.reflection.TestTypes.enumOf;
import static com.zapryan.reflection.TestTypes.listOf;
import static com.zapryan.reflection.TestTypes.parameter;
import static com.zapryan.reflection.TestTypes.setter;
import static org.junit.Assert.assertEquals;

public class InputWriterTest {

    private final InputWriter writer = new InputWriter(new VariableNames());
    @Test
    public void writesLiteralsInPlaceAndBeansAsVariablesInTheOrderTheRunBuiltThem() {
        TypeDescriptor.BeanType address = bean("Address");
        address.define(List.of(constructor()), List.of(setter(address, "street", STRING)));
        TypeDescriptor.BeanType person = bean("Person");
        person.define(List.of(constructor(parameter("name", STRING), parameter("age", INT))),
                List.of(setter(person, "email", STRING), setter(person, "address", address)));
        TypeDescriptor.settle(List.of(address, person));

        ValueSpec ann = new ValueSpec.Constructed("com.acme.Person", List.of("java.lang.String", "int"),
                List.of(literal("Ann"), literal(30)), List.of(
                new SetterCall("com.acme.Person", "setEmail", "java.lang.String", literal("ann@example.com")),
                new SetterCall("com.acme.Person", "setAddress", "com.acme.Address", new ValueSpec.Constructed(
                        "com.acme.Address", List.of(), List.of(),
                        List.of(new SetterCall("com.acme.Address", "setStreet", "java.lang.String", literal("Main")))))));

        assertEquals("person, 2", writer.arguments(staticMethod(parameter("person", person), parameter("count", INT)),
                List.of(ann, literal(2))));
        assertEquals(List.of(
                "com.acme.Person person = new com.acme.Person(\"Ann\", 30);",
                "person.setEmail(\"ann@example.com\");",
                "com.acme.Address address = new com.acme.Address();",
                "address.setStreet(\"Main\");",
                "person.setAddress(address);"), writer.getStatements());
    }

    @Test
    public void declaresCollectionsAndArraysAsTheirDeclaredTypeAndWritesEnumConstantsInPlace() {
        TypeDescriptor.BeanType item = bean("Item");
        item.define(List.of(constructor(parameter("code", INT))), List.of());
        TypeDescriptor.settle(List.of(item));
        MethodDescriptor fill = staticMethod(parameter("tags", listOf(STRING)), parameter("scores", arrayOf(INT)),
                parameter("items", listOf(item)), parameter("status", enumOf("Status", "OPEN", "CLOSED")));

        String arguments = writer.arguments(fill, List.of(
                new ValueSpec.CollectionOf("java.util.ArrayList", List.of(literal("a"), literal("b"))),
                new ValueSpec.ArrayOf("int", List.of(literal(1), literal(2))),
                new ValueSpec.CollectionOf("java.util.ArrayList", List.of(
                        new ValueSpec.Constructed("com.acme.Item", List.of("int"), List.of(literal(7))),
                        new ValueSpec.Constructed("com.acme.Item", List.of("int"), List.of(literal(8))))),
                new ValueSpec.EnumConstant("com.acme.Status", "CLOSED")));

        assertEquals("tags, scores, items, com.acme.Status.CLOSED", arguments);
        assertEquals(List.of(
                        "java.util.List<java.lang.String> tags = new java.util.ArrayList<>(java.util.Arrays.asList(\"a\", \"b\"));",
                        "int[] scores = new int[]{1, 2};",
                        "com.acme.Item item = new com.acme.Item(7);",
                        "com.acme.Item item2 = new com.acme.Item(8);",
                        "java.util.List<com.acme.Item> items = new java.util.ArrayList<>(java.util.Arrays.asList(item, item2));"),
                writer.getStatements());
    }

    @Test
    public void castsNullsAndLiteralsOfAnotherTypeThanTheirParameterInCaseTheMethodIsOverloaded() {
        assertEquals("(java.lang.Integer) (-5), (java.lang.String) null, 7", writer.arguments(
                staticMethod(parameter("count", INTEGER), parameter("note", STRING), parameter("size", INT)),
                List.of(literal(-5), new ValueSpec.Null(), literal(7))));
    }

    @Test
    public void castsALoneNullElementSoItIsAnElementAndNotTheArray() {
        writer.arguments(staticMethod(parameter("tags", listOf(STRING))),
                List.of(new ValueSpec.CollectionOf("java.util.ArrayList", List.of(new ValueSpec.Null()))));

        assertEquals(List.of("java.util.List<java.lang.String> tags = "
                + "new java.util.ArrayList<>(java.util.Arrays.asList((java.lang.String) null));"), writer.getStatements());
    }

    @Test
    public void createsTheReceiverWithADiamondWhenItsClassIsGeneric() {
        MethodDescriptor size = new MethodDescriptor("com.acme.Cache", "com.acme.Cache", "size", "int", false, false,
                List.of(), new ReceiverDescriptor(ReceiverDescriptor.Kind.INSTANTIABLE,
                List.of(constructor(), constructor(parameter("capacity", INT)))));

        String receiver = writer.receiver(size,
                new ValueSpec.Constructed("com.acme.Cache", List.of("int"), List.of(literal(10))), "com.acme.Cache<?>");

        assertEquals("cache", receiver);
        assertEquals(List.of("com.acme.Cache<?> cache = new com.acme.Cache<>(10);"), writer.getStatements());
    }

    @Test
    public void erasesTypeArgumentsForArrayCreation() {
        assertEquals("java.util.Map[]", InputWriter.withoutTypeArguments("java.util.Map<K, java.util.List<V>>[]"));
    }

    private static MethodDescriptor staticMethod(ParameterDescriptor... parameters) {
        return new MethodDescriptor("com.acme.Shop", "com.acme.Shop", "run", "void", true, false, List.of(parameters),
                new ReceiverDescriptor(ReceiverDescriptor.Kind.INSTANTIABLE, List.of(constructor())));
    }

    private static ValueSpec literal(Serializable value) {
        return new ValueSpec.Literal(value);
    }
}
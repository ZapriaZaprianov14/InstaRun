package com.zapryan.json;

import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zapryan.json.TestJson.array;
import static com.zapryan.json.TestJson.bool;
import static com.zapryan.json.TestJson.member;
import static com.zapryan.json.TestJson.nothing;
import static com.zapryan.json.TestJson.number;
import static com.zapryan.json.TestJson.object;
import static com.zapryan.json.TestJson.string;
import static com.zapryan.reflection.TestTypes.BIG_DECIMAL;
import static com.zapryan.reflection.TestTypes.BOOLEAN;
import static com.zapryan.reflection.TestTypes.BYTE;
import static com.zapryan.reflection.TestTypes.CHAR;
import static com.zapryan.reflection.TestTypes.DOUBLE;
import static com.zapryan.reflection.TestTypes.INT;
import static com.zapryan.reflection.TestTypes.INTEGER;
import static com.zapryan.reflection.TestTypes.LONG;
import static com.zapryan.reflection.TestTypes.SHORT;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class JsonBinderTest {

    private static final TypeDescriptor.EnumType STATUS = enumOf("Status", "ACTIVE", "RETIRED");
    private static final TypeDescriptor.UnsupportedType MAP = unsupported("Map<String, Object>");
    private static final TypeDescriptor.BeanType ADDRESS = bean("Address");
    private static final TypeDescriptor.BeanType PERSON = bean("Person");
    private static final TypeDescriptor.BeanType POINT = bean("Point");
    private static final TypeDescriptor.BeanType PAIR = bean("Pair");

    static {
        ADDRESS.define(Collections.singletonList(constructor()),
                Arrays.asList(setter(ADDRESS, "street", STRING), setter(ADDRESS, "number", INT)));
        PERSON.define(
                Arrays.asList(constructor(parameter("name", STRING)),
                        constructor(parameter("name", STRING), parameter("age", INT))),
                Arrays.asList(setter(PERSON, "email", STRING), setter(PERSON, "address", ADDRESS),
                        setter(PERSON, "nicknames", listOf(STRING)), setter(PERSON, "status", STATUS),
                        setter(PERSON, "meta", MAP)));
        POINT.define(Collections.singletonList(constructor(parameter("x", INT), parameter("y", INT))),
                Collections.emptyList());
        PAIR.define(Arrays.asList(constructor(parameter("left", INT)), constructor(parameter("right", INT))),
                Collections.emptyList());
        TypeDescriptor.settle(Arrays.asList(ADDRESS, PERSON, POINT, PAIR));
    }

    @Test
    public void bindsLiteralsFromTheMatchingKindOfJsonValue() throws Exception {
        assertEquals(new ValueSpec.Literal("text"), JsonBinder.bind(string("text"), STRING));
        assertEquals(new ValueSpec.Literal('x'), JsonBinder.bind(string("x"), CHAR));
        assertEquals(new ValueSpec.Literal(true), JsonBinder.bind(bool(true), BOOLEAN));
        assertEquals(new ValueSpec.Literal((short) 7), JsonBinder.bind(number("7"), SHORT));
        assertEquals(new ValueSpec.Literal(2.5), JsonBinder.bind(number("2.5"), DOUBLE));
    }

    @Test
    public void readsNumbersFromTheirExactText() throws Exception {
        // 2^53 + 1 has no exact double, so going through double would change it.
        assertEquals(new ValueSpec.Literal(9007199254740993L), JsonBinder.bind(number("9007199254740993"), LONG));
        assertEquals(new ValueSpec.Literal(new BigDecimal("0.10")), JsonBinder.bind(number("0.10"), BIG_DECIMAL));
    }

    @Test
    public void rejectsNumbersThatDoNotFitTheDeclaredType() {
        assertProblem(JsonProblem.INVALID_LITERAL, "", Arrays.asList("300", "byte"),
                () -> JsonBinder.bind(number("300"), BYTE));
        assertProblem(JsonProblem.INVALID_LITERAL, "", Arrays.asList("4.5", "int"),
                () -> JsonBinder.bind(number("4.5"), INT));
        assertProblem(JsonProblem.INVALID_LITERAL, "", Arrays.asList("1e3", "int"),
                () -> JsonBinder.bind(number("1e3"), INT));
    }

    @Test
    public void rejectsTheWrongKindOfJsonValue() {
        assertProblem(JsonProblem.EXPECTED_NUMBER, "", Collections.singletonList("int"),
                () -> JsonBinder.bind(string("7"), INT));
        assertProblem(JsonProblem.EXPECTED_STRING, "", Collections.singletonList("String"),
                () -> JsonBinder.bind(number("7"), STRING));
        assertProblem(JsonProblem.EXPECTED_BOOLEAN, "", Collections.singletonList("boolean"),
                () -> JsonBinder.bind(string("true"), BOOLEAN));
        assertProblem(JsonProblem.EXPECTED_STRING, "", Collections.singletonList("Status"),
                () -> JsonBinder.bind(number("0"), STATUS));
        assertProblem(JsonProblem.EXPECTED_ARRAY, "", Collections.singletonList("List<String>"),
                () -> JsonBinder.bind(object(), listOf(STRING)));
        assertProblem(JsonProblem.EXPECTED_OBJECT, "", Collections.singletonList("Address"),
                () -> JsonBinder.bind(array(), ADDRESS));
    }

    @Test
    public void acceptsNullForEverythingButPrimitives() throws Exception {
        assertEquals(new ValueSpec.Null(), JsonBinder.bind(nothing(), INTEGER));
        assertEquals(new ValueSpec.Null(), JsonBinder.bind(nothing(), ADDRESS));
        assertEquals(new ValueSpec.Null(), JsonBinder.bind(nothing(), listOf(STRING)));
        assertProblem(JsonProblem.NULL_FOR_PRIMITIVE, "", Collections.singletonList("int"),
                () -> JsonBinder.bind(nothing(), INT));
    }

    @Test
    public void charactersMustBeExactlyOneCharacterLong() {
        assertProblem(JsonProblem.INVALID_LITERAL, "", Arrays.asList("ab", "char"),
                () -> JsonBinder.bind(string("ab"), CHAR));
        assertProblem(JsonProblem.INVALID_LITERAL, "", Arrays.asList("", "char"),
                () -> JsonBinder.bind(string(""), CHAR));
    }

    @Test
    public void bindsEnumConstantsByName() throws Exception {
        assertEquals(new ValueSpec.EnumConstant("com.acme.Status", "RETIRED"), JsonBinder.bind(string("RETIRED"), STATUS));
        assertProblem(JsonProblem.UNKNOWN_ENUM_CONSTANT, "", Arrays.asList("Retired", "Status", "ACTIVE, RETIRED"),
                () -> JsonBinder.bind(string("Retired"), STATUS));
    }

    @Test
    public void bindsCollectionsAndArraysElementByElement() throws Exception {
        assertEquals(new ValueSpec.CollectionOf("java.util.ArrayList", Arrays.asList(new ValueSpec.Literal("a"), new ValueSpec.Null())),
                JsonBinder.bind(array(string("a"), nothing()), listOf(STRING)));
        assertEquals(new ValueSpec.ArrayOf("int", Arrays.<ValueSpec>asList(new ValueSpec.Literal(1), new ValueSpec.Literal(2))),
                JsonBinder.bind(array(number("1"), number("2")), arrayOf(INT)));
    }

    @Test
    public void choosesTheWidestConstructorWhoseParametersAreAllGiven() throws Exception {
        assertEquals(new ValueSpec.Constructed("com.acme.Person", Arrays.asList("java.lang.String", "int"),
                        Arrays.<ValueSpec>asList(new ValueSpec.Literal("Ann"), new ValueSpec.Literal(31))),
                JsonBinder.bind(object(member("age", number("31")), member("name", string("Ann"))), PERSON));
        assertEquals(new ValueSpec.Constructed("com.acme.Person", Collections.singletonList("java.lang.String"),
                        Collections.<ValueSpec>singletonList(new ValueSpec.Literal("Ann"))),
                JsonBinder.bind(object(member("name", string("Ann"))), PERSON));
    }

    @Test
    public void setsTheOtherPropertiesThroughSettersInJsonOrder() throws Exception {
        ValueSpec person = JsonBinder.bind(object(
                member("status", string("ACTIVE")),
                member("name", string("Ann")),
                member("email", string("ann@example.com"))), PERSON);

        assertEquals(new ValueSpec.Constructed("com.acme.Person", Collections.singletonList("java.lang.String"),
                Collections.<ValueSpec>singletonList(new ValueSpec.Literal("Ann")), Arrays.asList(
                new SetterCall("com.acme.Person", "setStatus", "com.acme.Status",
                        new ValueSpec.EnumConstant("com.acme.Status", "ACTIVE")),
                new SetterCall("com.acme.Person", "setEmail", "java.lang.String",
                        new ValueSpec.Literal("ann@example.com")))), person);
    }

    @Test
    public void bindsNestedBeans() throws Exception {
        ValueSpec person = JsonBinder.bind(object(
                member("name", string("Ann")),
                member("address", object(member("street", string("Main")), member("number", number("5"))))), PERSON);

        ValueSpec address = new ValueSpec.Constructed("com.acme.Address", Collections.<String>emptyList(),
                Collections.<ValueSpec>emptyList(), Arrays.asList(
                new SetterCall("com.acme.Address", "setStreet", "java.lang.String", new ValueSpec.Literal("Main")),
                new SetterCall("com.acme.Address", "setNumber", "int", new ValueSpec.Literal(5))));
        assertEquals(new ValueSpec.Constructed("com.acme.Person", Collections.singletonList("java.lang.String"),
                        Collections.<ValueSpec>singletonList(new ValueSpec.Literal("Ann")),
                        Collections.singletonList(new SetterCall("com.acme.Person", "setAddress", "com.acme.Address", address))),
                person);
    }

    @Test
    public void reportsWhereInTheValueAProblemIs() {
        assertProblem(JsonProblem.EXPECTED_NUMBER, ".address.number", Collections.singletonList("int"),
                () -> JsonBinder.bind(object(member("name", string("Ann")),
                        member("address", object(member("number", string("five"))))), PERSON));
        assertProblem(JsonProblem.EXPECTED_STRING, ".nicknames[1]", Collections.singletonList("String"),
                () -> JsonBinder.bind(object(member("name", string("Ann")),
                        member("nicknames", array(string("A"), number("7")))), PERSON));
    }

    @Test
    public void reportsWhatTheClosestConstructorStillNeeds() {
        assertProblem(JsonProblem.NO_MATCHING_CONSTRUCTOR, "", Arrays.asList("Point", "y"),
                () -> JsonBinder.bind(object(member("x", number("1"))), POINT));
    }

    @Test
    public void rejectsUnknownAndRepeatedProperties() {
        assertProblem(JsonProblem.UNKNOWN_PROPERTY, "",
                Arrays.asList("nmae", "Person", "name, age, email, address, nicknames, status, meta"),
                () -> JsonBinder.bind(object(member("name", string("Ann")), member("nmae", string("Bo"))), PERSON));
        assertProblem(JsonProblem.DUPLICATE_PROPERTY, "", Collections.singletonList("name"),
                () -> JsonBinder.bind(object(member("name", string("Ann")), member("name", string("Bo"))), PERSON));
    }

    @Test
    public void reportsAPropertyOnlyAConstructorThatWasNotChosenTakes() {
        assertProblem(JsonProblem.NOT_SETTABLE, "", Arrays.asList("right", "Pair"),
                () -> JsonBinder.bind(object(member("left", number("1")), member("right", number("2"))), PAIR));
    }

    @Test
    public void refusesValuesOfTypesThatCannotBeEnteredButAcceptsNullForThem() throws Exception {
        JsonBindingException problem = assertProblem(JsonProblem.UNSUPPORTED_TYPE, ".meta",
                Collections.singletonList("Map<String, Object>"),
                () -> JsonBinder.bind(object(member("name", string("Ann")), member("meta", object())), PERSON));
        assertSame(MAP, problem.getType());

        ValueSpec person = JsonBinder.bind(object(member("name", string("Ann")), member("meta", nothing())), PERSON);
        assertEquals(Collections.singletonList(new SetterCall("com.acme.Person", "setMeta", "java.util.Map", new ValueSpec.Null())),
                ((ValueSpec.Constructed) person).getSetterCalls());
    }

    @Test
    public void pointsAtTheOffendingTextWhenTheJsonWasParsed() {
        JsonBindingException badNumber = assertThrows(JsonBindingException.class,
                () -> JsonBinder.bind(new JsonNode.NumberNode("4.5", 17), INT));
        assertEquals(17, badNumber.getOffset());

        JsonNode.ObjectNode withTypo = new JsonNode.ObjectNode(Arrays.asList(
                new JsonNode.Member("name", string("Ann"), 4), new JsonNode.Member("nmae", string("Bo"), 19)), 2);
        assertEquals(19, assertThrows(JsonBindingException.class, () -> JsonBinder.bind(withTypo, PERSON)).getOffset());
    }

    private static JsonBindingException assertProblem(JsonProblem problem, String path, List<String> arguments,
                                                      ThrowingRunnable binding) {
        JsonBindingException exception = assertThrows(JsonBindingException.class, binding);
        assertEquals(problem, exception.getProblem());
        assertEquals(path, exception.getPath());
        assertEquals(arguments, exception.getArguments());
        return exception;
    }
}
package com.zapryan.reflection;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zapryan.reflection.TestTypes.INT;
import static com.zapryan.reflection.TestTypes.INTEGER;
import static com.zapryan.reflection.TestTypes.STRING;
import static com.zapryan.reflection.TestTypes.arrayOf;
import static com.zapryan.reflection.TestTypes.bean;
import static com.zapryan.reflection.TestTypes.constructor;
import static com.zapryan.reflection.TestTypes.listOf;
import static com.zapryan.reflection.TestTypes.literal;
import static com.zapryan.reflection.TestTypes.parameter;
import static com.zapryan.reflection.TestTypes.setter;
import static com.zapryan.reflection.TestTypes.unsupported;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TypeDescriptorTest {

    @Test
    public void aBeanWhoseEveryConstructorNeedsSomethingUnsupportedIsUnsupportedAndSoIsWhatNeedsIt() {
        TypeDescriptor.BeanType outer = bean("Outer");
        TypeDescriptor.BeanType inner = bean("Inner");
        outer.define(Collections.singletonList(constructor(parameter("inner", inner))), Collections.emptyList());
        inner.define(Collections.singletonList(constructor(parameter("meta", unsupported("Map<String, Object>")))),
                Collections.emptyList());

        TypeDescriptor.settle(Arrays.asList(outer, inner));

        assertFalse(inner.isSupported());
        assertFalse(outer.isSupported());
        assertSame(outer, outer.getUnsupportedCause());
        assertSame(inner, listOf(inner).getUnsupportedCause());
    }

    @Test
    public void beansThatNeedEachOtherAreSupportedBecauseNullEndsTheCycle() {
        TypeDescriptor.BeanType chicken = bean("Chicken");
        TypeDescriptor.BeanType egg = bean("Egg");
        chicken.define(Collections.singletonList(constructor(parameter("egg", egg))), Collections.emptyList());
        egg.define(Collections.singletonList(constructor(parameter("chicken", chicken))), Collections.emptyList());

        TypeDescriptor.settle(Arrays.asList(chicken, egg));

        assertTrue(chicken.isSupported());
        assertTrue(egg.isSupported());
    }

    @Test
    public void propertiesListTheWidestUsableConstructorFirstThenSetters() {
        TypeDescriptor.BeanType person = bean("Person");
        person.define(
                Arrays.asList(constructor(parameter("name", STRING)),
                        constructor(parameter("meta", unsupported("Object")), parameter("id", INT), parameter("tag", STRING)),
                        constructor(parameter("age", INT), parameter("name", STRING))),
                Arrays.asList(setter(person, "email", STRING), setter(person, "name", STRING),
                        setter(person, "scores", arrayOf(INTEGER))));
        TypeDescriptor.settle(Collections.singletonList(person));

        List<String> names = new ArrayList<>();
        for (PropertyDescriptor property : person.getProperties()) {
            names.add(property.getName());
        }

        assertEquals(Arrays.asList("age", "name", "email", "scores"), names);
    }

    @Test
    public void findsSettersByPropertyName() {
        TypeDescriptor.BeanType person = bean("Person");
        SetterDescriptor email = setter(person, "email", STRING);
        person.define(Collections.singletonList(constructor()), Collections.singletonList(email));

        assertSame(email, person.findSetter("email"));
        assertNull(person.findSetter("name"));
    }

    @Test
    public void setterNamesFollowTheJavaBeansRules() {
        assertEquals("firstName", SetterDescriptor.propertyNameOf("setFirstName"));
        assertEquals("x", SetterDescriptor.propertyNameOf("setX"));
        assertEquals("URL", SetterDescriptor.propertyNameOf("setURL"));
        assertNull(SetterDescriptor.propertyNameOf("set"));
        assertNull(SetterDescriptor.propertyNameOf("settle"));
        assertNull(SetterDescriptor.propertyNameOf("reset"));
    }

    @Test
    public void declaredCollectionTypesGetAnImplementationThatKeepsTheEnteredOrder() {
        assertEquals("java.util.ArrayList", TypeDescriptor.CollectionType.implementationOf("java.util.List"));
        assertEquals("java.util.LinkedHashSet", TypeDescriptor.CollectionType.implementationOf("java.util.Set"));
        assertEquals("java.util.TreeSet", TypeDescriptor.CollectionType.implementationOf("java.util.SortedSet"));
        assertEquals("java.util.LinkedList", TypeDescriptor.CollectionType.implementationOf("java.util.Queue"));
        assertNull(TypeDescriptor.CollectionType.implementationOf("java.util.Map"));
    }

    @Test
    public void onlyPrimitivesCannotBeNull() {
        assertFalse(INT.isNullable());
        assertTrue(INTEGER.isNullable());
        assertTrue(listOf(INT).isNullable());
        assertTrue(bean("Anything").isNullable());
    }

    @Test
    public void literalsParseIntoTheirExactBox() {
        assertEquals(Boolean.TRUE, literal("boolean").parse("true"));
        assertEquals(Byte.valueOf((byte) 7), literal("byte").parse("7"));
        assertEquals(Character.valueOf('x'), literal("char").parse("x"));
        assertEquals(Short.valueOf((short) 300), literal("short").parse("300"));
        assertEquals(Integer.valueOf(-42), literal("int").parse("-42"));
        assertEquals(Long.valueOf(9_000_000_000L), literal("long").parse("9000000000"));
        assertEquals(Float.valueOf(1.5f), literal("float").parse("1.5"));
        assertEquals(Double.valueOf(2.25), literal("double").parse("2.25"));
        assertEquals(Integer.valueOf(5), literal("java.lang.Integer").parse("5"));
        assertEquals("text", literal("java.lang.String").parse("text"));
        assertEquals(new BigInteger("123456789012345678901234567890"),
                literal("java.math.BigInteger").parse("123456789012345678901234567890"));
        assertEquals(new BigDecimal("0.10"), literal("java.math.BigDecimal").parse("0.10"));
    }

    @Test
    public void literalsTrimNumbersAndBooleansButNotTextOrCharacters() {
        assertEquals(Integer.valueOf(7), literal("int").parse(" 7 "));
        assertEquals(Boolean.FALSE, literal("java.lang.Boolean").parse(" FALSE "));
        assertEquals(" padded ", literal("java.lang.String").parse(" padded "));
        assertEquals(Character.valueOf(' '), literal("char").parse(" "));
    }

    @Test
    public void literalsRejectValuesOutsideTheirRangeOrMalformed() {
        assertThrows(IllegalArgumentException.class, () -> literal("byte").parse("300"));
        assertThrows(IllegalArgumentException.class, () -> literal("int").parse("3000000000"));
        assertThrows(IllegalArgumentException.class, () -> literal("int").parse("4.5"));
        assertThrows(IllegalArgumentException.class, () -> literal("long").parse(""));
        assertThrows(IllegalArgumentException.class, () -> literal("double").parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> literal("boolean").parse("yes"));
        assertThrows(IllegalArgumentException.class, () -> literal("char").parse("ab"));
        assertThrows(IllegalArgumentException.class, () -> literal("char").parse(""));
    }

    @Test
    public void knowsWhichTypesAreLiteralsAndHowEachIsWritten() {
        assertTrue(TypeDescriptor.LiteralType.isSupported("int"));
        assertTrue(TypeDescriptor.LiteralType.isSupported("java.lang.String"));
        assertFalse(TypeDescriptor.LiteralType.isSupported("java.util.List<java.lang.String>"));
        assertFalse(TypeDescriptor.LiteralType.isSupported("java.util.Date"));
        assertThrows(IllegalArgumentException.class, () -> literal("java.util.Date"));

        assertEquals(TypeDescriptor.LiteralType.Kind.TEXT, literal("java.lang.String").getKind());
        assertEquals(TypeDescriptor.LiteralType.Kind.CHARACTER, literal("char").getKind());
        assertEquals(TypeDescriptor.LiteralType.Kind.BOOLEAN, literal("java.lang.Boolean").getKind());
        assertEquals(TypeDescriptor.LiteralType.Kind.INTEGER, literal("java.math.BigInteger").getKind());
        assertEquals(TypeDescriptor.LiteralType.Kind.INTEGER, literal("short").getKind());
        assertEquals(TypeDescriptor.LiteralType.Kind.DECIMAL, literal("float").getKind());
        assertEquals(TypeDescriptor.LiteralType.Kind.DECIMAL, literal("java.math.BigDecimal").getKind());
    }
}
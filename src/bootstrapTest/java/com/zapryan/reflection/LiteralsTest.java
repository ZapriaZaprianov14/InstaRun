package com.zapryan.reflection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LiteralsTest {

    @Test
    public void parsesEachTypeIntoItsExactBox() {
        assertEquals(Boolean.TRUE, Literals.parse("boolean", "true"));
        assertEquals(Byte.valueOf((byte) 7), Literals.parse("byte", "7"));
        assertEquals(Character.valueOf('x'), Literals.parse("char", "x"));
        assertEquals(Short.valueOf((short) 300), Literals.parse("short", "300"));
        assertEquals(Integer.valueOf(-42), Literals.parse("int", "-42"));
        assertEquals(Long.valueOf(9_000_000_000L), Literals.parse("long", "9000000000"));
        assertEquals(Float.valueOf(1.5f), Literals.parse("float", "1.5"));
        assertEquals(Double.valueOf(2.25), Literals.parse("double", "2.25"));
        assertEquals(Integer.valueOf(5), Literals.parse("java.lang.Integer", "5"));
        assertEquals("text", Literals.parse("java.lang.String", "text"));
    }

    @Test
    public void trimsNumbersAndBooleansButNotStringsOrCharacters() {
        assertEquals(Integer.valueOf(7), Literals.parse("int", " 7 "));
        assertEquals(Boolean.FALSE, Literals.parse("java.lang.Boolean", " FALSE "));
        assertEquals(" padded ", Literals.parse("java.lang.String", " padded "));
        assertEquals(Character.valueOf(' '), Literals.parse("char", " "));
    }

    @Test
    public void rejectsNumbersOutsideTheDeclaredRange() {
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("byte", "300"));
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("int", "3000000000"));
    }

    @Test
    public void rejectsMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("int", "4.5"));
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("long", ""));
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("double", "abc"));
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("boolean", "yes"));
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("char", "ab"));
        assertThrows(IllegalArgumentException.class, () -> Literals.parse("char", ""));
    }

    @Test
    public void knowsWhichTypesAreSupportedAndWhichCanBeNull() {
        assertTrue(Literals.isSupported("int"));
        assertTrue(Literals.isSupported("java.lang.String"));
        assertFalse(Literals.isSupported("java.util.List<java.lang.String>"));
        assertFalse(Literals.isNullable("int"));
        assertTrue(Literals.isNullable("java.lang.Integer"));
        assertTrue(Literals.isNullable("java.lang.String"));
    }
}
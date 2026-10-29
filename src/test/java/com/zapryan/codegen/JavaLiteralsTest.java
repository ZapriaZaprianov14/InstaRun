package com.zapryan.codegen;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class JavaLiteralsTest {

    @Test
    public void writesEachLiteralSoItBoxesBackToItsOwnClass() {
        assertEquals("5", JavaLiterals.of(5));
        assertEquals("5L", JavaLiterals.of(5L));
        assertEquals("(short) 5", JavaLiterals.of((short) 5));
        assertEquals("(byte) -5", JavaLiterals.of((byte) -5));
        assertEquals("1.5f", JavaLiterals.of(1.5f));
        assertEquals("1.5", JavaLiterals.of(1.5));
        assertEquals("1.0E10", JavaLiterals.of(1e10));
        assertEquals("true", JavaLiterals.of(true));
        assertEquals("'a'", JavaLiterals.of('a'));
        assertEquals("-2147483648", JavaLiterals.of(Integer.MIN_VALUE));
        assertEquals("-9223372036854775808L", JavaLiterals.of(Long.MIN_VALUE));
    }

    @Test
    public void writesBigNumbersWithTheirExactDigitsAndScale() {
        assertEquals("new java.math.BigDecimal(\"1.50\")", JavaLiterals.of(new BigDecimal("1.50")));
        assertEquals("new java.math.BigInteger(\"123456789012345678901\")",
                JavaLiterals.of(new BigInteger("123456789012345678901")));
    }

    @Test
    public void writesSpecialFloatingPointValuesAsConstants() {
        assertEquals("java.lang.Double.NaN", JavaLiterals.of(Double.NaN));
        assertEquals("java.lang.Double.NEGATIVE_INFINITY", JavaLiterals.of(Double.NEGATIVE_INFINITY));
        assertEquals("java.lang.Float.POSITIVE_INFINITY", JavaLiterals.of(Float.POSITIVE_INFINITY));
    }

    @Test
    public void escapesStringsAndCharacters() {
        assertEquals("\"say \\\"hi\\\" \\\\ \\n\\t\\001 \u00e9 'ok'\"", JavaLiterals.of("say \"hi\" \\ \n\t\u0001 \u00e9 'ok'"));
        assertEquals("'\\''", JavaLiterals.of('\''));
        assertEquals("'\"'", JavaLiterals.of('"'));
        assertEquals("'\\n'", JavaLiterals.of('\n'));
        assertEquals("'\\000'", JavaLiterals.of('\0'));
    }

    @Test
    public void escapesOnlySurrogatesThatAreNotPaired() {
        assertEquals("\"\uD83D\uDE00\"", JavaLiterals.of("\uD83D\uDE00"));
        assertEquals("\"\\ud83d!\"", JavaLiterals.of("\uD83D!"));
    }

    @Test
    public void writesBoxedValuesAsExpressionsOfTheirWrapperType() {
        assertEquals("java.lang.Integer.valueOf(5)", JavaLiterals.boxed(5));
        assertEquals("java.lang.Short.valueOf((short) 5)", JavaLiterals.boxed((short) 5));
        assertEquals("java.lang.Boolean.TRUE", JavaLiterals.boxed(true));
        assertEquals("\"text\"", JavaLiterals.boxed("text"));
    }

    @Test
    public void knowsTheSourceTypeOfEachLiteral() {
        assertEquals("int", JavaLiterals.typeOf(5));
        assertEquals("short", JavaLiterals.typeOf((short) 5));
        assertEquals("char", JavaLiterals.typeOf('a'));
        assertEquals("boolean", JavaLiterals.typeOf(true));
        assertEquals("java.lang.String", JavaLiterals.typeOf("a"));
        assertEquals("java.math.BigDecimal", JavaLiterals.typeOf(BigDecimal.ONE));
    }

    @Test
    public void parenthesizesANegativeOperandOfACast() {
        assertEquals("(java.lang.Integer) (-5)", JavaLiterals.cast("java.lang.Integer", "-5"));
        assertEquals("(java.lang.String) null", JavaLiterals.cast("java.lang.String", "null"));
    }
}
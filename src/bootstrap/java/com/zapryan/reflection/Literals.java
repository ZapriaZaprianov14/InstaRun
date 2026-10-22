package com.zapryan.reflection;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The types a value can be typed in for: primitives, their wrappers, String, BigInteger and BigDecimal. Parsing is
 * strict and uses the declared type's own parser, so "300" is rejected for a byte instead of being silently narrowed.
 */
public final class Literals {

    /** How a value is written: as text, a single character, true or false, a whole number or a decimal number. */
    public enum Kind { TEXT, CHARACTER, BOOLEAN, INTEGER, DECIMAL }

    private static final Set<String> PRIMITIVES = new HashSet<>(Arrays.asList(
            "boolean", "byte", "char", "short", "int", "long", "float", "double"));
    private static final Set<String> NULLABLE = new HashSet<>(Arrays.asList(
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.String",
            "java.math.BigInteger", "java.math.BigDecimal"));

    private Literals() {
    }

    public static boolean isSupported(String typeName) {
        return PRIMITIVES.contains(typeName) || NULLABLE.contains(typeName);
    }

    public static boolean isNullable(String typeName) {
        return NULLABLE.contains(typeName);
    }

    /** Returns null for a type that is not a literal type. */
    public static @Nullable Kind kindOf(String typeName) {
        switch (typeName) {
            case "java.lang.String":
                return Kind.TEXT;
            case "char":
            case "java.lang.Character":
                return Kind.CHARACTER;
            case "boolean":
            case "java.lang.Boolean":
                return Kind.BOOLEAN;
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "long":
            case "java.lang.Long":
            case "java.math.BigInteger":
                return Kind.INTEGER;
            case "float":
            case "java.lang.Float":
            case "double":
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return Kind.DECIMAL;
            default:
                return null;
        }
    }

    /**
     * @throws IllegalArgumentException if the text is not a valid value of the type
     */
    public static Serializable parse(String typeName, String text) {
        switch (typeName) {
            case "java.lang.String":
                return text;
            case "char":
            case "java.lang.Character":
                if (text.length() != 1) {
                    throw new IllegalArgumentException("Not a single character: " + text);
                }
                return text.charAt(0);
            case "boolean":
            case "java.lang.Boolean":
                return parseBoolean(text.trim());
            case "byte":
            case "java.lang.Byte":
                return Byte.valueOf(text.trim());
            case "short":
            case "java.lang.Short":
                return Short.valueOf(text.trim());
            case "int":
            case "java.lang.Integer":
                return Integer.valueOf(text.trim());
            case "long":
            case "java.lang.Long":
                return Long.valueOf(text.trim());
            case "float":
            case "java.lang.Float":
                return Float.valueOf(text.trim());
            case "double":
            case "java.lang.Double":
                return Double.valueOf(text.trim());
            case "java.math.BigInteger":
                return new BigInteger(text.trim());
            case "java.math.BigDecimal":
                return new BigDecimal(text.trim());
            default:
                throw new IllegalArgumentException("Unsupported type: " + typeName);
        }
    }

    private static Boolean parseBoolean(String text) {
        if (text.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (text.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Not true or false: " + text);
    }
}
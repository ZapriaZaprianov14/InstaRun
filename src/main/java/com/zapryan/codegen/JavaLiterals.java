package com.zapryan.codegen;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Writes values as Java source. Class names are fully qualified; the IDE shortens them once the code is inserted.
 */
final class JavaLiterals {

    private JavaLiterals() {
    }

    /**
     * The value as a literal of its own primitive type when it is a boxed primitive, so that it boxes back to exactly
     * its class: {@code 5L}, {@code (short) 5}, {@code 'a'}. Strings as string literals, and BigIntegers and
     * BigDecimals as expressions that create an equal value, scale included.
     */
    static String of(Object value) {
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Character) {
            return character((Character) value);
        }
        if (value instanceof Boolean || value instanceof Integer) {
            return value.toString();
        }
        if (value instanceof Byte) {
            return "(byte) " + value;
        }
        if (value instanceof Short) {
            return "(short) " + value;
        }
        if (value instanceof Long) {
            return value + "L";
        }
        if (value instanceof Float) {
            return floatLiteral((Float) value);
        }
        if (value instanceof Double) {
            return doubleLiteral((Double) value);
        }
        if (value instanceof BigInteger) {
            return "new java.math.BigInteger(" + quote(value.toString()) + ")";
        }
        if (value instanceof BigDecimal) {
            return "new java.math.BigDecimal(" + quote(value.toString()) + ")";
        }
        throw new IllegalArgumentException("Not a literal value: " + value.getClass().getName());
    }

    /** The type a literal written by {@link #of} has in source, e.g. {@code int} for an Integer. */
    static String typeOf(Object value) {
        return switch (value.getClass().getSimpleName()) {
            case "Boolean" -> "boolean";
            case "Character" -> "char";
            case "Byte" -> "byte";
            case "Short" -> "short";
            case "Integer" -> "int";
            case "Long" -> "long";
            case "Float" -> "float";
            case "Double" -> "double";
            default -> value.getClass().getName();
        };
    }

    /**
     * An expression whose compile-time type is the value's wrapper class, e.g. {@code java.lang.Integer.valueOf(5)},
     * for comparing against an expression of a reference type without ambiguous overloads; other values as {@link #of}.
     */
    static String boxed(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value ? "java.lang.Boolean.TRUE" : "java.lang.Boolean.FALSE";
        }
        if (value instanceof Character || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double) {
            return "java.lang." + value.getClass().getSimpleName() + ".valueOf(" + of(value) + ")";
        }
        return of(value);
    }

    /** A cast of the expression; an operand starting with a minus sign is parenthesized, as a reference cast needs. */
    static String cast(String typeText, String expression) {
        return "(" + typeText + ") " + (expression.startsWith("-") ? "(" + expression + ")" : expression);
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else {
                appendEscaped(c, out, isUnpairedSurrogate(text, i));
            }
        }
        return out.append('"').toString();
    }

    private static String character(char c) {
        StringBuilder out = new StringBuilder("'");
        if (c == '\'') {
            out.append("\\'");
        } else {
            appendEscaped(c, out, Character.isSurrogate(c));
        }
        return out.append('\'').toString();
    }

    private static void appendEscaped(char c, StringBuilder out, boolean unpairedSurrogate) {
        switch (c) {
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            default -> {
                if (c < 0x20 || c == 0x7f) {
                    // Octal: unicode escapes are translated before the code is parsed, so a newline one ends the line.
                    out.append(String.format("\\%03o", (int) c));
                } else if (unpairedSurrogate) {
                    out.append(String.format("\\u%04x", (int) c));
                } else {
                    out.append(c);
                }
            }
        }
    }

    private static boolean isUnpairedSurrogate(String text, int index) {
        char c = text.charAt(index);
        if (Character.isHighSurrogate(c)) {
            return index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1));
        }
        if (Character.isLowSurrogate(c)) {
            return index == 0 || !Character.isHighSurrogate(text.charAt(index - 1));
        }
        return false;
    }

    private static String floatLiteral(float value) {
        if (Float.isNaN(value)) {
            return "java.lang.Float.NaN";
        }
        if (Float.isInfinite(value)) {
            return value > 0 ? "java.lang.Float.POSITIVE_INFINITY" : "java.lang.Float.NEGATIVE_INFINITY";
        }
        return value + "f";
    }

    private static String doubleLiteral(double value) {
        if (Double.isNaN(value)) {
            return "java.lang.Double.NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "java.lang.Double.POSITIVE_INFINITY" : "java.lang.Double.NEGATIVE_INFINITY";
        }
        return Double.toString(value);
    }
}
package com.zapryan.json;

/**
 * Why JSON text doesn't give a value of its declared type. The plugin turns each problem into a message; the
 * arguments each problem carries are listed with it, in order.
 */
public enum JsonProblem {
    /** The text is not valid JSON. Arguments: the parser's description of the error. */
    SYNTAX_ERROR,
    /** Nothing but whitespace was entered. */
    EMPTY,
    /** More than one top-level value was entered. */
    MULTIPLE_VALUES,
    /** A value JSON doesn't allow, such as an unquoted word. Arguments: its text. */
    NOT_A_VALUE,
    /** The declared type can't be entered yet; the exception carries it. Arguments: the type. */
    UNSUPPORTED_TYPE,
    /** Arguments: the type. */
    NULL_FOR_PRIMITIVE,
    /** Arguments: the type. */
    EXPECTED_STRING,
    /** Arguments: the type. */
    EXPECTED_BOOLEAN,
    /** Arguments: the type. */
    EXPECTED_NUMBER,
    /** Arguments: the type. */
    EXPECTED_OBJECT,
    /** Arguments: the type. */
    EXPECTED_ARRAY,
    /** Arguments: the text, the type. */
    INVALID_LITERAL,
    /** Arguments: the text, the enum type, its constants. */
    UNKNOWN_ENUM_CONSTANT,
    /** Arguments: the property, the bean type, its properties. */
    UNKNOWN_PROPERTY,
    /** Arguments: the property. */
    DUPLICATE_PROPERTY,
    /** Arguments: the bean type, the properties the closest constructor still needs. */
    NO_MATCHING_CONSTRUCTOR,
    /**
     * The property is only a parameter of a constructor that wasn't chosen. Arguments: the property, the bean type.
     */
    NOT_SETTABLE
}
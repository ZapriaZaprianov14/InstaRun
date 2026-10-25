package com.zapryan.ui;

import com.zapryan.json.JsonBindingException;
import com.zapryan.json.JsonProblem;
import com.zapryan.reflection.TypeDescriptor;

/** Turns the reflection model's reasons and problems into the messages shown to the user. */
public final class InputMessages {

    private InputMessages() {
    }

    /** Why no value of the type can be entered yet, naming the type that is the actual obstacle. */
    public static String whyUnsupported(TypeDescriptor type) {
        TypeDescriptor cause = type.getUnsupportedCause();
        if (cause instanceof TypeDescriptor.UnsupportedType) {
            return MessageBundler.message("type.unsupported", cause.getDisplayName(),
                    ((TypeDescriptor.UnsupportedType) cause).getReason());
        }
        return MessageBundler.message("type.unsupported.noUsableConstructor",
                cause != null ? cause.getDisplayName() : type.getDisplayName());
    }

    /** Describes a problem with the JSON of a value, e.g. {@code order.items[2].price: '1.5' is not a valid int.} */
    public static String describe(JsonBindingException problem, String valueName) {
        String message = problem.getProblem() == JsonProblem.UNSUPPORTED_TYPE && problem.getType() != null
                ? MessageBundler.message("json.problem.unsupportedType", whyUnsupported(problem.getType()))
                : MessageBundler.message(keyOf(problem.getProblem()), problem.getArguments().toArray());
        return MessageBundler.message("json.problem.at", valueName + problem.getPath(), message);
    }

    private static String keyOf(JsonProblem problem) {
        return switch (problem) {
            case SYNTAX_ERROR -> "json.problem.syntaxError";
            case EMPTY -> "json.problem.empty";
            case MULTIPLE_VALUES -> "json.problem.multipleValues";
            case NOT_A_VALUE -> "json.problem.notAValue";
            case UNSUPPORTED_TYPE -> "json.problem.unsupportedType";
            case NULL_FOR_PRIMITIVE -> "json.problem.nullForPrimitive";
            case EXPECTED_STRING -> "json.problem.expectedString";
            case EXPECTED_BOOLEAN -> "json.problem.expectedBoolean";
            case EXPECTED_NUMBER -> "json.problem.expectedNumber";
            case EXPECTED_OBJECT -> "json.problem.expectedObject";
            case EXPECTED_ARRAY -> "json.problem.expectedArray";
            case INVALID_LITERAL -> "json.problem.invalidLiteral";
            case UNKNOWN_ENUM_CONSTANT -> "json.problem.unknownEnumConstant";
            case UNKNOWN_PROPERTY -> "json.problem.unknownProperty";
            case DUPLICATE_PROPERTY -> "json.problem.duplicateProperty";
            case NO_MATCHING_CONSTRUCTOR -> "json.problem.noMatchingConstructor";
            case NOT_SETTABLE -> "json.problem.notSettable";
        };
    }
}
package com.zapryan.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.zapryan.json.JsonBindingException;
import com.zapryan.json.JsonProblem;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.RunBlocker;
import com.zapryan.reflection.TypeDescriptor;

import java.util.List;
import java.util.stream.Collectors;

/** Turns the reflection model's reasons and problems into the messages shown to the user. */
public final class InputMessages {

    private InputMessages() {
    }

    /** Why the method can't be run, e.g. {@code Cannot run 'save()'. It is an instance method of ...}. */
    public static String whyBlocked(MethodDescriptor method, List<RunBlocker> blockers) {
        String reasons = blockers.stream().map(blocker -> describe(blocker, method)).collect(Collectors.joining(" "));
        return MessageBundler.message("run.blocked", method.getMethodName(), reasons);
    }

    private static String describe(RunBlocker blocker, MethodDescriptor method) {
        String className = StringUtil.getShortName(method.getDeclaringClassFqn());
        return switch (blocker) {
            case CONSTRUCTOR -> MessageBundler.message("run.blocker.constructor");
            case LOCAL_OR_ANONYMOUS_CLASS -> MessageBundler.message("run.blocker.localOrAnonymousClass");
            case ABSTRACT_RECEIVER -> MessageBundler.message("run.blocker.abstractReceiver", className);
            case ENUM_RECEIVER -> MessageBundler.message("run.blocker.enumReceiver", className);
            case INNER_CLASS_RECEIVER -> MessageBundler.message("run.blocker.innerClassReceiver", className);
            case SPRING_BEAN_RECEIVER -> MessageBundler.message("run.blocker.springBeanReceiver", className,
                    method.getReceiver().getSpringEvidence());
            case NO_USABLE_CONSTRUCTOR -> MessageBundler.message("run.blocker.noUsableConstructor", className);
            case UNSUPPORTED_PARAMETER_TYPE ->
                    MessageBundler.message("run.blocker.unsupportedParameterType", unsupportedParameters(method));
            case GETTER_OR_SETTER -> MessageBundler.message("run.blocker.getterOrSetter");
        };
    }

    private static String unsupportedParameters(MethodDescriptor method) {
        return method.getParameters().stream()
                .filter(parameter -> !parameter.isSupported())
                .map(parameter -> MessageBundler.message("run.blocker.unsupportedParameter", parameter.getName(),
                        whyUnsupported(parameter.getType())))
                .collect(Collectors.joining("; "));
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
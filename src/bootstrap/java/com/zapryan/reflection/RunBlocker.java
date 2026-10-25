package com.zapryan.reflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A reason the reflection engine cannot run a method yet. The action stays visible for such methods
 * and reports every blocker instead of silently hiding itself.
 */
public enum RunBlocker {
    CONSTRUCTOR,
    LOCAL_OR_ANONYMOUS_CLASS,
    ABSTRACT_RECEIVER,
    ENUM_RECEIVER,
    INNER_CLASS_RECEIVER,
    NO_USABLE_CONSTRUCTOR,
    UNSUPPORTED_PARAMETER_TYPE,
    GETTER_OR_SETTER,
    SPRING_BEAN_RECEIVER;

    public static List<RunBlocker> findAll(MethodDescriptor method) {
        if (method.isConstructor()) {
            return Collections.singletonList(CONSTRUCTOR);
        }
        List<RunBlocker> blockers = new ArrayList<>();
        if (method.getDeclaringClassBinaryName() == null) {
            blockers.add(LOCAL_OR_ANONYMOUS_CLASS);
        } else if (!method.isStatic()) {
            RunBlocker receiverBlocker = receiverBlocker(method.getReceiver());
            if (receiverBlocker != null) {
                blockers.add(receiverBlocker);
            }
        }
        if (!method.getParameters().stream().allMatch(ParameterDescriptor::isSupported)) {
            blockers.add(UNSUPPORTED_PARAMETER_TYPE);
        }
        if (isGetterOrSetter(method)) {
            blockers.add(GETTER_OR_SETTER);
        }
        return blockers;
    }

    /**
     * A JavaBeans-shaped accessor or mutator: {@code setX(value)}, {@code getX()}, or {@code isX()} returning
     * boolean. Only checked for instance methods; a static {@code getInstance()}-style factory is not one of these.
     */
    private static boolean isGetterOrSetter(MethodDescriptor method) {
        if (method.isStatic()) {
            return false;
        }
        String name = method.getMethodName();
        List<ParameterDescriptor> parameters = method.getParameters();
        if (parameters.size() == 1) {
            return SetterDescriptor.propertyNameOf(name) != null;
        }
        if (!parameters.isEmpty()) {
            return false;
        }
        if (startsWithUppercaseAfter(name, "get")) {
            return !"void".equals(method.getReturnTypeFqn());
        }
        if (startsWithUppercaseAfter(name, "is")) {
            return "boolean".equals(method.getReturnTypeFqn()) || "java.lang.Boolean".equals(method.getReturnTypeFqn());
        }
        return false;
    }

    private static boolean startsWithUppercaseAfter(String name, String prefix) {
        return name.length() > prefix.length() && name.startsWith(prefix)
                && Character.isUpperCase(name.charAt(prefix.length()));
    }

    private static RunBlocker receiverBlocker(ReceiverDescriptor receiver) {
        switch (receiver.getKind()) {
            case ABSTRACT:
                return ABSTRACT_RECEIVER;
            case ENUM:
                return ENUM_RECEIVER;
            case INNER_CLASS:
                return INNER_CLASS_RECEIVER;
            case SPRING_BEAN:
                return SPRING_BEAN_RECEIVER;
            default:
                return receiver.getUsableConstructors().isEmpty() ? NO_USABLE_CONSTRUCTOR : null;
        }
    }
}
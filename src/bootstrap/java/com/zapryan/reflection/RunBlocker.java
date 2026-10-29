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
    SPRING_BEAN_RECEIVER,
    NO_USABLE_CONSTRUCTOR,
    UNSUPPORTED_PARAMETER_TYPE,
    GETTER_OR_SETTER;

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
        if (method.isGetterOrSetter()) {
            blockers.add(GETTER_OR_SETTER);
        }
        return blockers;
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
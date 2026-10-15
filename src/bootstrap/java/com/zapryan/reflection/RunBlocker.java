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
    NOT_STATIC,
    HAS_PARAMETERS;

    public static List<RunBlocker> findAll(MethodDescriptor method) {
        if (method.isConstructor()) {
            return Collections.singletonList(CONSTRUCTOR);
        }
        List<RunBlocker> blockers = new ArrayList<>();
        if (method.getDeclaringClassBinaryName() == null) {
            blockers.add(LOCAL_OR_ANONYMOUS_CLASS);
        }
        if (!method.isStatic()) {
            blockers.add(NOT_STATIC);
        }
        if (!method.getParameters().isEmpty()) {
            blockers.add(HAS_PARAMETERS);
        }
        return blockers;
    }
}
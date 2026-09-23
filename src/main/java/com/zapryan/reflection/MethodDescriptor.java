package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public final class MethodDescriptor {

    private final String declaringClassFqn;
    private final String methodName;
    private final String returnTypeFqn;
    private final boolean isStatic;
    private final List<ParameterDescriptor> parameters;
}

package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
@AllArgsConstructor
public final class MethodDescriptor {

    private final String declaringClassFqn;
    /** JVM binary name ({@code Outer$Inner}) for {@link Class#forName}; null for local and anonymous classes. */
    @Nullable
    private final String declaringClassBinaryName;
    private final String methodName;
    private final String returnTypeFqn;
    private final boolean isStatic;
    private final boolean isConstructor;
    private final List<ParameterDescriptor> parameters;
    private final ReceiverDescriptor receiver;
}
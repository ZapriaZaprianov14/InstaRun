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
    /**
     * Whether it is an instance method that is a plain getter or setter: named like one, and only returning a field
     * or only setting one from its parameter.
     */
    private final boolean getterOrSetter;

    /** A method that is not a plain getter or setter. */
    public MethodDescriptor(String declaringClassFqn, @Nullable String declaringClassBinaryName, String methodName,
                            String returnTypeFqn, boolean isStatic, boolean isConstructor,
                            List<ParameterDescriptor> parameters, ReceiverDescriptor receiver) {
        this(declaringClassFqn, declaringClassBinaryName, methodName, returnTypeFqn, isStatic, isConstructor,
                parameters, receiver, false);
    }
}
package com.zapryan.bootstrap;

import com.zapryan.reflection.ValueSpec;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Recipe for one reflective call. It is deserialized on the target project's JDK (possibly Java 8),
 * so fields must be JDK types that serialize identically there, e.g. ArrayList but never List.of().
 */
@Value
public class InvocationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    String declaringClassBinaryName;
    String methodName;
    List<String> parameterTypeNames;
    /** How to create the object the method is called on; null for static methods. */
    @Nullable
    ValueSpec receiver;
    List<ValueSpec> arguments;

    public InvocationRequest(String declaringClassBinaryName, String methodName, List<String> parameterTypeNames,
                             @Nullable ValueSpec receiver, List<ValueSpec> arguments) {
        this.declaringClassBinaryName = declaringClassBinaryName;
        this.methodName = methodName;
        this.parameterTypeNames = new ArrayList<>(parameterTypeNames);
        this.receiver = receiver;
        this.arguments = new ArrayList<>(arguments);
    }
}
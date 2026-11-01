package com.instarun.bootstrap;

import com.instarun.reflection.ValueSpec;
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
    /** Where to write the {@link Outcome} of the call, to generate a test from it or to show the result; or null. */
    @Nullable
    String outcomeFile;
    /**
     * Whether the outcome is recorded to show the user what the method returned, rather than to generate a test: the
     * value is then recorded through its own class, and the run ends like any program instead of right away.
     */
    boolean shown;

    public InvocationRequest(String declaringClassBinaryName, String methodName, List<String> parameterTypeNames,
                             @Nullable ValueSpec receiver, List<ValueSpec> arguments) {
        this(declaringClassBinaryName, methodName, parameterTypeNames, receiver, arguments, null);
    }

    public InvocationRequest(String declaringClassBinaryName, String methodName, List<String> parameterTypeNames,
                             @Nullable ValueSpec receiver, List<ValueSpec> arguments, @Nullable String outcomeFile) {
        this(declaringClassBinaryName, methodName, parameterTypeNames, receiver, arguments, outcomeFile, false);
    }

    public InvocationRequest(String declaringClassBinaryName, String methodName, List<String> parameterTypeNames,
                             @Nullable ValueSpec receiver, List<ValueSpec> arguments, @Nullable String outcomeFile,
                             boolean shown) {
        this.declaringClassBinaryName = declaringClassBinaryName;
        this.methodName = methodName;
        this.parameterTypeNames = new ArrayList<>(parameterTypeNames);
        this.receiver = receiver;
        this.arguments = new ArrayList<>(arguments);
        this.outcomeFile = outcomeFile;
        this.shown = shown;
    }
}
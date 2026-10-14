package com.zapryan.bootstrap;

import lombok.Value;

import java.io.Serializable;

/**
 * Recipe for one reflective call. It is deserialized on the target project's JDK (possibly Java 8),
 * so fields must be JDK types that serialize identically there, e.g. ArrayList but never List.of().
 */
@Value
public class InvocationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    String declaringClassBinaryName;
    String methodName;
}
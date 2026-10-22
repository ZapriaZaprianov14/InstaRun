package com.zapryan.reflection;

import lombok.Value;

import java.io.Serializable;

/**
 * One setter call on a {@link ValueSpec.Constructed} instance. The setter is looked up on the class that declares it,
 * by its parameter's erased type, so inherited and non-public setters are found too.
 */
@Value
public class SetterCall implements Serializable {

    private static final long serialVersionUID = 1L;

    String declaringClassBinaryName;
    String methodName;
    /** JVM name of the parameter type's erasure, e.g. {@code java.util.List} for {@code setItems(List<Item>)}. */
    String parameterTypeName;
    ValueSpec value;
}
package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class ParameterDescriptor {
    private final String name;
    /** The type, fully qualified and with its type arguments, e.g. {@code java.util.List<com.acme.Order>}. */
    private final String typeFqn;
    /** JVM name of the type's erasure, which reflection looks the method up by, e.g. {@code java.util.List}. */
    private final String erasedTypeName;
    private final TypeDescriptor type;

    public boolean isSupported() {
        return type.isSupported();
    }
}
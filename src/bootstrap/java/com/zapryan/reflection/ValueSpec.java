package com.zapryan.reflection;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A recipe for one value, turned into a real object inside the launched JVM. It is deserialized there
 * (possibly on Java 8), so fields may only hold JDK types Java 8 can read, e.g. ArrayList but never List.of().
 */
public abstract class ValueSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private ValueSpec() {
    }

    /**
     * A String, BigInteger, BigDecimal or boxed primitive whose class matches the declared type exactly:
     * reflection won't turn an Integer into a short.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Literal extends ValueSpec {
        private static final long serialVersionUID = 1L;

        Serializable value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Null extends ValueSpec {
        private static final long serialVersionUID = 1L;
    }

    /**
     * A new instance, created by the constructor with exactly these parameter types and then given the properties
     * the constructor doesn't take through their setters, in order.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Constructed extends ValueSpec {
        private static final long serialVersionUID = 1L;

        String classBinaryName;
        List<String> parameterTypeNames;
        List<ValueSpec> arguments;
        List<SetterCall> setterCalls;

        public Constructed(String classBinaryName, List<String> parameterTypeNames, List<ValueSpec> arguments) {
            this(classBinaryName, parameterTypeNames, arguments, Collections.<SetterCall>emptyList());
        }

        public Constructed(String classBinaryName, List<String> parameterTypeNames, List<ValueSpec> arguments,
                           List<SetterCall> setterCalls) {
            this.classBinaryName = classBinaryName;
            this.parameterTypeNames = new ArrayList<>(parameterTypeNames);
            this.arguments = new ArrayList<>(arguments);
            this.setterCalls = new ArrayList<>(setterCalls);
        }
    }

    /** A new array holding the elements in order. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class ArrayOf extends ValueSpec {
        private static final long serialVersionUID = 1L;

        /** JVM name of the component type, e.g. {@code int}, {@code com.acme.Order} or {@code java.lang.String[]}. */
        String componentTypeName;
        List<ValueSpec> elements;

        public ArrayOf(String componentTypeName, List<ValueSpec> elements) {
            this.componentTypeName = componentTypeName;
            this.elements = new ArrayList<>(elements);
        }
    }

    /** A new collection of the given class, created by its no-argument constructor and filled in order. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class CollectionOf extends ValueSpec {
        private static final long serialVersionUID = 1L;

        String collectionClassBinaryName;
        List<ValueSpec> elements;

        public CollectionOf(String collectionClassBinaryName, List<ValueSpec> elements) {
            this.collectionClassBinaryName = collectionClassBinaryName;
            this.elements = new ArrayList<>(elements);
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class EnumConstant extends ValueSpec {
        private static final long serialVersionUID = 1L;

        String enumClassBinaryName;
        String constantName;
    }
}
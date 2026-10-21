package com.zapryan.reflection;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A recipe for one value, turned into a real object inside the launched JVM. It is deserialized there
 * (possibly on Java 8), so fields may only hold JDK types Java 8 can read, e.g. ArrayList but never List.of().
 */
public abstract class ValueSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private ValueSpec() {
    }

    /** A String or boxed primitive whose box matches the declared type exactly: reflection won't turn an Integer into a short. */
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

    /** A new instance, created by the constructor with exactly these parameter types. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Constructed extends ValueSpec {
        private static final long serialVersionUID = 1L;

        String classBinaryName;
        List<String> parameterTypeNames;
        List<ValueSpec> arguments;

        public Constructed(String classBinaryName, List<String> parameterTypeNames, List<ValueSpec> arguments) {
            this.classBinaryName = classBinaryName;
            this.parameterTypeNames = new ArrayList<>(parameterTypeNames);
            this.arguments = new ArrayList<>(arguments);
        }
    }
}
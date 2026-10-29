package com.zapryan.bootstrap;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a value looked like when the method returned it, as far as a test can read it through the value's declared
 * type: an object only through the getters that type has, a list by index only when it is declared as a List. It is
 * recorded in the launched JVM so the plugin can write assertions about it, and travels back to the IDE serialized,
 * so fields may only hold JDK types that read the same on both sides, e.g. ArrayList but never List.of().
 */
public abstract class Observation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Observation() {
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Null extends Observation {
        private static final long serialVersionUID = 1L;
    }

    /** A String, Character, Boolean, boxed number, BigInteger or BigDecimal, of exactly that class. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Literal extends Observation {
        private static final long serialVersionUID = 1L;

        Serializable value;
        /** Whether it is declared as a primitive, e.g. returned by a getter of type int. */
        boolean primitive;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class EnumConstant extends Observation {
        private static final long serialVersionUID = 1L;

        /** The enum's name as the test writes it, e.g. {@code com.acme.Order.Status}; null if the test can't see it. */
        @Nullable
        String enumName;
        String constantName;
    }

    /**
     * An array declared as an array, or a collection declared as a collection: how many elements it had, and the
     * first of them in iteration order.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Sequence extends Observation {
        private static final long serialVersionUID = 1L;

        public enum Kind {
            ARRAY,
            /** A list declared as a List, so the test can reach its elements by index. */
            LIST,
            /** A set, which the test compares as a set. */
            SET,
            /** Any other collection, a list declared as some other collection type included: read in order. */
            OTHER_COLLECTION
        }

        Kind kind;
        int size;
        /** Fewer than {@link #size} when only the first ones were recorded. */
        List<Observation> elements;
        /**
         * For an array whose elements can be written as literals, its declared element type, e.g. {@code int} or
         * {@code java.lang.String}, so the test can compare the whole array; null otherwise.
         */
        @Nullable
        String literalElementType;

        public Sequence(Kind kind, int size, List<Observation> elements, @Nullable String literalElementType) {
            this.kind = kind;
            this.size = size;
            this.elements = new ArrayList<>(elements);
            this.literalElementType = literalElementType;
        }

        public boolean isComplete() {
            return elements.size() == size;
        }
    }

    /** A map declared as a Map: how many entries it had, and the first of them in iteration order. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class MapOf extends Observation {
        private static final long serialVersionUID = 1L;

        int size;
        /** Fewer than {@link #size} when only the first ones were recorded. */
        List<Entry> entries;

        public MapOf(int size, List<Entry> entries) {
            this.size = size;
            this.entries = new ArrayList<>(entries);
        }
    }

    @Value
    public static class Entry implements Serializable {
        private static final long serialVersionUID = 1L;

        Observation key;
        Observation value;
    }

    /**
     * An object recorded through the getters, or a record's accessors, that its declared type gives the test, by the
     * accessor's method name; there is at least one.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Bean extends Observation {
        private static final long serialVersionUID = 1L;

        String classBinaryName;
        Map<String, Observation> properties;

        public Bean(String classBinaryName, Map<String, Observation> properties) {
            this.classBinaryName = classBinaryName;
            this.properties = new LinkedHashMap<>(properties);
        }
    }

    /** A JDK object recorded through its toString, e.g. a LocalDate or a UUID. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Text extends Observation {
        private static final long serialVersionUID = 1L;

        String classBinaryName;
        String text;
    }

    /** A value that isn't null but wasn't recorded, and why. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Unrecorded extends Observation {
        private static final long serialVersionUID = 1L;

        public enum Reason {
            /** It contains itself, through the value being recorded. */
            CYCLE,
            /** It is nested deeper than values are recorded. */
            TOO_DEEP,
            /** Recording stopped after the most values it takes. */
            LIMIT_REACHED,
            /** Reading it threw; the detail names the exception. */
            THREW,
            /** The test has no way to read it, e.g. it is declared as Object, or its type has no getters. */
            OPAQUE
        }

        Reason reason;
        @Nullable
        String detail;
    }
}
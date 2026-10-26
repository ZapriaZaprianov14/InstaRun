package com.zapryan.bootstrap;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * How a call went, written to a file for the plugin when a test is being generated from it. Read in the IDE, so
 * fields follow the same rules as {@link Observation}'s.
 */
public abstract class Outcome implements Serializable {

    private static final long serialVersionUID = 1L;

    private Outcome() {
    }

    /** The class of an exception and its superclasses, the exception's own class first and Throwable last. */
    static List<String> classesOf(Throwable exception) {
        List<String> classes = new ArrayList<>();
        for (Class<?> type = exception.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            classes.add(type.getName());
        }
        return classes;
    }

    /** The method returned a value. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Returned extends Outcome {
        private static final long serialVersionUID = 1L;

        Observation value;
    }

    /** A void method returned. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class ReturnedVoid extends Outcome {
        private static final long serialVersionUID = 1L;
    }

    /** The method threw. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Threw extends Outcome {
        private static final long serialVersionUID = 1L;

        /** The exception's class first, then its superclasses up to Throwable. */
        List<String> exceptionClasses;
        @Nullable
        String message;

        public Threw(List<String> exceptionClasses, @Nullable String message) {
            this.exceptionClasses = new ArrayList<>(exceptionClasses);
            this.message = message;
        }

        static Threw of(Throwable exception) {
            return new Threw(classesOf(exception), exception.getMessage());
        }
    }

    /** Creating the receiver or an argument threw, so the method was never called. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class SetupThrew extends Outcome {
        private static final long serialVersionUID = 1L;

        /** The exception's class first, then its superclasses up to Throwable. */
        List<String> exceptionClasses;
        @Nullable
        String message;

        public SetupThrew(List<String> exceptionClasses, @Nullable String message) {
            this.exceptionClasses = new ArrayList<>(exceptionClasses);
            this.message = message;
        }

        static SetupThrew of(Throwable exception) {
            return new SetupThrew(classesOf(exception), exception.getMessage());
        }
    }

    /** The call couldn't be set up at all, e.g. a class wasn't found; the reason is the bootstrap's own message. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class NotRun extends Outcome {
        private static final long serialVersionUID = 1L;

        String reason;
    }
}
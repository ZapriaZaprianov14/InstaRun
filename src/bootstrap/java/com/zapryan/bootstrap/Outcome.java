package com.zapryan.bootstrap;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * How a call went, written to a file for the plugin when a test is being generated from it. Read in the IDE, so
 * fields follow the same rules as {@link Observation}'s.
 */
public abstract class Outcome implements Serializable {

    private static final long serialVersionUID = 1L;

    private Outcome() {
    }

    /** The method returned a value. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Returned extends Outcome {
        private static final long serialVersionUID = 1L;

        /**
         * What the test declares the result as: the return type, or its erasure when the test can't write the type
         * itself, e.g. {@code java.util.List<com.acme.Item>}, or else {@code java.lang.Object}.
         */
        String type;
        Observation value;

        static Returned of(Method method, @Nullable Object result) {
            Observer observer = new Observer(Observer.packageOf(method.getDeclaringClass()));
            Type type = observer.declarable(method.getGenericReturnType());
            return new Returned(Observer.typeText(type), observer.observe(result, type));
        }
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

        /**
         * The exception's class as the test writes it, e.g. {@code java.lang.IllegalStateException}, or its nearest
         * superclass the test can see.
         */
        String exceptionClass;
        @Nullable
        String message;

        static Threw of(Method method, Throwable exception) {
            String testPackage = Observer.packageOf(method.getDeclaringClass());
            String name = null;
            // Throwable itself is public, so this ends there at the latest.
            for (Class<?> type = exception.getClass(); name == null; type = type.getSuperclass()) {
                name = Observer.nameInTest(type, testPackage);
            }
            return new Threw(name, exception.getMessage());
        }
    }

    /** Creating the receiver or an argument threw, so the method was never called. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class SetupThrew extends Outcome {
        private static final long serialVersionUID = 1L;

        String exceptionClassBinaryName;
        @Nullable
        String message;

        static SetupThrew of(Throwable exception) {
            return new SetupThrew(exception.getClass().getName(), exception.getMessage());
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
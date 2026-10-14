package com.zapryan.bootstrap;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Main class of the launched JVM. Exits with 1 when the invoked method throws, and with
 * {@link #EXIT_BOOTSTRAP_FAILURE} when the method could not be reached at all.
 */
public final class InstaRunBootstrap {

    public static final String OUTPUT_PREFIX = "[InstaRun] ";
    public static final String RESULT_PREFIX = OUTPUT_PREFIX + "Result: ";
    public static final int EXIT_BOOTSTRAP_FAILURE = 2;

    private InstaRunBootstrap() {
    }

    public static void main(String[] args) throws Throwable {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws Throwable {
        Method method;
        try {
            method = resolveMethod(decodeRequest(args));
        } catch (BootstrapFailure failure) {
            err.println(OUTPUT_PREFIX + failure.getMessage());
            return EXIT_BOOTSTRAP_FAILURE;
        }

        Object result;
        try {
            result = method.invoke(null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        out.println(RESULT_PREFIX + (method.getReturnType() == void.class ? "void" : String.valueOf(result)));
        return 0;
    }

    private static InvocationRequest decodeRequest(String[] args) throws BootstrapFailure {
        if (args.length != 1) {
            throw new BootstrapFailure(
                    "Expected exactly one argument, the encoded invocation request, but got " + args.length + ".");
        }
        try {
            return InvocationRequestCodec.decode(args[0]);
        } catch (IllegalArgumentException | IOException | ClassNotFoundException e) {
            throw new BootstrapFailure("Could not decode the invocation request: " + e);
        }
    }

    private static Method resolveMethod(InvocationRequest request) throws BootstrapFailure {
        String className = request.getDeclaringClassBinaryName();
        String methodLabel = className + "#" + request.getMethodName() + "()";

        Class<?> declaringClass;
        try {
            declaringClass = Class.forName(className, false, InstaRunBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new BootstrapFailure("Class " + className + " was not found on the classpath.");
        } catch (LinkageError e) {
            throw new BootstrapFailure("Class " + className + " could not be loaded: " + e);
        }

        Method method;
        try {
            method = declaringClass.getDeclaredMethod(request.getMethodName());
        } catch (NoSuchMethodException e) {
            throw new BootstrapFailure(
                    "Method " + methodLabel + " was not found; the compiled class may be out of date.");
        } catch (LinkageError e) {
            // getDeclaredMethod resolves the signature of every method in the class, not only the requested one.
            throw new BootstrapFailure("The methods of " + className + " could not be read: " + e);
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            throw new BootstrapFailure("Method " + methodLabel + " is not static in the compiled class.");
        }

        try {
            method.setAccessible(true);
        } catch (RuntimeException e) {
            throw new BootstrapFailure("Method " + methodLabel + " could not be made accessible: " + e);
        }
        return method;
    }

    private static final class BootstrapFailure extends Exception {
        BootstrapFailure(String message) {
            super(message);
        }
    }
}
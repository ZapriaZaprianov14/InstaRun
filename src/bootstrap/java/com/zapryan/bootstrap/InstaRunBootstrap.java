package com.zapryan.bootstrap;

import com.zapryan.reflection.ValueSpec;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Main class of the launched JVM. Exits with 1 when the user's code throws (the method or the receiver's
 * constructor), and with {@link #EXIT_BOOTSTRAP_FAILURE} when the call could not be set up at all.
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
        ValueMaterializer values = new ValueMaterializer(InstaRunBootstrap.class.getClassLoader());
        Method method;
        Object result;
        try {
            InvocationRequest request = decodeRequest(args);
            method = resolveMethod(request, values);
            Object receiver = receiverFor(method, request, values);
            result = method.invoke(receiver, values.materializeAll(request.getArguments()));
        } catch (BootstrapFailure failure) {
            err.println(OUTPUT_PREFIX + failure.getMessage());
            return EXIT_BOOTSTRAP_FAILURE;
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

    private static Method resolveMethod(InvocationRequest request, ValueMaterializer values) throws BootstrapFailure {
        Class<?> declaringClass = values.loadClass(request.getDeclaringClassBinaryName());
        Class<?>[] parameterTypes = values.resolveTypes(request.getParameterTypeNames());
        Method method;
        try {
            method = declaringClass.getDeclaredMethod(request.getMethodName(), parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new BootstrapFailure(
                    "Method " + methodLabel(request) + " was not found; the compiled class may be out of date.");
        } catch (LinkageError e) {
            // getDeclaredMethod resolves the signature of every method in the class, not only the requested one.
            throw new BootstrapFailure("The methods of " + declaringClass.getName() + " could not be read: " + e);
        }
        ValueMaterializer.makeAccessible(method, "Method " + methodLabel(request));
        return method;
    }

    private static Object receiverFor(Method method, InvocationRequest request, ValueMaterializer values)
            throws BootstrapFailure, ReflectiveOperationException {
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        ValueSpec receiver = request.getReceiver();
        if (receiver == null) {
            throw new BootstrapFailure("Method " + methodLabel(request)
                    + " is not static in the compiled class, and no receiver was given to call it on.");
        }
        return values.materialize(receiver);
    }

    private static String methodLabel(InvocationRequest request) {
        return ValueMaterializer.signature(
                request.getDeclaringClassBinaryName() + "#" + request.getMethodName(), request.getParameterTypeNames());
    }
}
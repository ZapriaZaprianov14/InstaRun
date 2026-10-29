package com.zapryan.bootstrap;

import com.zapryan.reflection.ValueSpec;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Main class of the launched JVM. Exits with 1 when the user's code throws (the method or the receiver's
 * constructor), and with {@link #EXIT_BOOTSTRAP_FAILURE} when the call could not be set up at all. When a test is
 * being generated, it also records how the call went in the request's outcome file, and then exits right away even
 * if the call left threads running, so the test doesn't wait on them.
 */
public final class InstaRunBootstrap {

    public static final String OUTPUT_PREFIX = "[InstaRun] ";
    public static final String RESULT_PREFIX = OUTPUT_PREFIX + "Result: ";
    public static final int EXIT_BOOTSTRAP_FAILURE = 2;
    public static final int EXIT_USER_EXCEPTION = 1;

    private InstaRunBootstrap() {
    }

    public static void main(String[] args) throws Throwable {
        boolean recordsOutcome = outcomeFileOf(args) != null;
        int exitCode;
        try {
            exitCode = run(args, System.out, System.err);
        } catch (Throwable thrown) {
            if (!recordsOutcome) {
                throw thrown;
            }
            // Printed the way the JVM prints an uncaught exception, but exiting below also stops leftover threads.
            System.err.print("Exception in thread \"main\" ");
            thrown.printStackTrace();
            exitCode = EXIT_USER_EXCEPTION;
        }
        if (exitCode != 0 || recordsOutcome) {
            System.out.flush();
            System.err.flush();
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws Throwable {
        ValueMaterializer values = new ValueMaterializer(InstaRunBootstrap.class.getClassLoader());
        InvocationRequest request;
        try {
            request = decodeRequest(args);
        } catch (BootstrapFailure failure) {
            err.println(OUTPUT_PREFIX + failure.getMessage());
            return EXIT_BOOTSTRAP_FAILURE;
        }
        String outcomeFile = request.getOutcomeFile();

        Method method;
        Object receiver;
        Object[] arguments;
        try {
            method = resolveMethod(request, values);
            receiver = receiverFor(method, request, values);
            arguments = values.materializeAll(request.getArguments());
        } catch (BootstrapFailure failure) {
            err.println(OUTPUT_PREFIX + failure.getMessage());
            record(outcomeFile, new Outcome.NotRun(failure.getMessage()), err);
            return EXIT_BOOTSTRAP_FAILURE;
        } catch (InvocationTargetException e) {
            record(outcomeFile, Outcome.SetupThrew.of(e.getCause()), err);
            throw e.getCause();
        } catch (ReflectiveOperationException e) {
            record(outcomeFile, new Outcome.NotRun(e.toString()), err);
            throw e;
        }

        Object result;
        try {
            result = method.invoke(receiver, arguments);
        } catch (InvocationTargetException e) {
            record(outcomeFile, Outcome.Threw.of(method, e.getCause()), err);
            throw e.getCause();
        } catch (IllegalAccessException | IllegalArgumentException e) {
            record(outcomeFile, new Outcome.NotRun(e.toString()), err);
            throw e;
        }
        boolean isVoid = method.getReturnType() == void.class;
        if (outcomeFile != null) {
            // Recorded before printing: the result's own toString might throw.
            record(outcomeFile, isVoid ? new Outcome.ReturnedVoid() : Outcome.Returned.of(method, result), err);
        }
        out.println(RESULT_PREFIX + (isVoid ? "void" : String.valueOf(result)));
        return 0;
    }

    private static @Nullable String outcomeFileOf(String[] args) {
        try {
            return decodeRequest(args).getOutcomeFile();
        } catch (BootstrapFailure failure) {
            return null;
        }
    }

    private static void record(@Nullable String outcomeFile, Outcome outcome, PrintStream err) {
        if (outcomeFile == null) {
            return;
        }
        try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(outcomeFile))) {
            stream.writeObject(outcome);
        } catch (IOException e) {
            err.println(OUTPUT_PREFIX + "Could not record how the call went for the test: " + e);
        }
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
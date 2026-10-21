package com.zapryan.bootstrap;

import com.zapryan.reflection.ValueSpec;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstaRunBootstrapTest {

    private static final String TARGETS = "com.zapryan.bootstrap.fixtures.TargetMethods";
    private static final String GREETER = TARGETS + "$Greeter";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    public void printsTheReturnValue() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "answer"));
        assertEquals("[InstaRun] Result: 42", stdout());
    }

    @Test
    public void printsNullReturnValues() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "nothing"));
        assertEquals("[InstaRun] Result: null", stdout());
    }

    @Test
    public void printsVoidForVoidMethods() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "noResult"));
        assertEquals("[InstaRun] Result: void", stdout());
    }

    @Test
    public void findsNestedClassesByBinaryName() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS + "$Nested", "name"));
        assertEquals("[InstaRun] Result: nested", stdout());
    }

    @Test
    public void invokesPrivateMethods() throws Throwable {
        assertEquals(0, invokeStatic(TARGETS, "hidden"));
        assertEquals("[InstaRun] Result: hidden", stdout());
    }

    @Test
    public void invokesPublicMethodsOfNonPublicClasses() throws Throwable {
        assertEquals(0, invokeStatic("com.zapryan.bootstrap.fixtures.PackagePrivateTarget", "greet"));
        assertEquals("[InstaRun] Result: hello", stdout());
    }

    @Test
    public void passesArguments() throws Throwable {
        assertEquals(0, invoke(TARGETS, "repeat", types("java.lang.String", "int"), null, literal("ab"), literal(3)));
        assertEquals("[InstaRun] Result: ababab", stdout());
    }

    @Test
    public void passesEveryPrimitiveType() throws Throwable {
        assertEquals(0, invoke(TARGETS, "primitives",
                types("boolean", "byte", "char", "short", "int", "long", "float", "double"), null,
                literal(true), literal((byte) 1), literal('c'), literal((short) 2), literal(3), literal(4L),
                literal(5.5f), literal(6.5d)));
        assertEquals("[InstaRun] Result: true 1 c 2 3 4 5.5 6.5", stdout());
    }

    @Test
    public void passesNullToWrapperParameters() throws Throwable {
        assertEquals(0, invoke(TARGETS, "describe", types("java.lang.Integer"), null, new ValueSpec.Null()));
        assertEquals("[InstaRun] Result: no value", stdout());
    }

    @Test
    public void callsInstanceMethodsOnAReceiverBuiltWithTheNoArgumentConstructor() throws Throwable {
        assertEquals(0, invoke(TARGETS, "instanceMethod", types(), receiver(TARGETS, types())));
        assertEquals("[InstaRun] Result: 7", stdout());
    }

    @Test
    public void buildsTheReceiverWithTheChosenConstructorEvenWhenItIsPrivate() throws Throwable {
        ValueSpec receiver = receiver(GREETER, types("java.lang.String"), literal("Hi"));

        assertEquals(0, invoke(GREETER, "greet", types("java.lang.String"), receiver, literal("Ann")));
        assertEquals("[InstaRun] Result: Hi, Ann", stdout());
    }

    @Test
    public void rethrowsTheMethodsOwnExceptionUnwrapped() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> invokeStatic(TARGETS, "fail"));

        assertEquals("boom", thrown.getMessage());
    }

    @Test
    public void rethrowsTheReceiverConstructorsOwnExceptionUnwrapped() {
        String exploding = TARGETS + "$Exploding";

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> invoke(exploding, "run", types(), receiver(exploding, types())));

        assertEquals("constructor boom", thrown.getMessage());
    }

    @Test
    public void reportsAMissingClass() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invokeStatic("com.example.Missing", "run"));
        assertTrue(stderr(), stderr().contains("Class com.example.Missing was not found"));
    }

    @Test
    public void reportsAMissingMethodWithItsParameterTypes() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invoke(TARGETS, "repeat", types("int"), null, literal(1)));
        assertTrue(stderr(), stderr().contains(TARGETS + "#repeat(int) was not found"));
    }

    @Test
    public void reportsAMissingConstructor() throws Throwable {
        ValueSpec receiver = receiver(GREETER, types("int"), literal(1));

        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE,
                invoke(GREETER, "greet", types("java.lang.String"), receiver, literal("Ann")));
        assertTrue(stderr(), stderr().contains("Constructor " + GREETER + "(int) was not found"));
    }

    @Test
    public void reportsAnInstanceMethodWithoutAReceiver() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invokeStatic(TARGETS, "instanceMethod"));
        assertTrue(stderr(), stderr().contains(TARGETS + "#instanceMethod() is not static"));
    }

    @Test
    public void reportsAMalformedRequest() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, run("not base64!"));
        assertTrue(stderr(), stderr().contains("Could not decode the invocation request"));
    }

    @Test
    public void reportsAMissingRequest() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, run());
        assertTrue(stderr(), stderr().contains("but got 0"));
    }

    private int invokeStatic(String className, String methodName) throws Throwable {
        return invoke(className, methodName, types(), null);
    }

    private int invoke(String className, String methodName, List<String> parameterTypes, ValueSpec receiver,
                       ValueSpec... arguments) throws Throwable {
        InvocationRequest request =
                new InvocationRequest(className, methodName, parameterTypes, receiver, Arrays.asList(arguments));
        return run(InvocationRequestCodec.encode(request));
    }

    private int run(String... args) throws Throwable {
        return InstaRunBootstrap.run(args, new PrintStream(out, true), new PrintStream(err, true));
    }

    private static List<String> types(String... names) {
        return names.length == 0 ? Collections.<String>emptyList() : Arrays.asList(names);
    }

    private static ValueSpec literal(Serializable value) {
        return new ValueSpec.Literal(value);
    }

    private static ValueSpec receiver(String className, List<String> parameterTypes, ValueSpec... arguments) {
        return new ValueSpec.Constructed(className, parameterTypes, Arrays.asList(arguments));
    }

    private String stdout() {
        return out.toString().trim();
    }

    private String stderr() {
        return err.toString().trim();
    }
}
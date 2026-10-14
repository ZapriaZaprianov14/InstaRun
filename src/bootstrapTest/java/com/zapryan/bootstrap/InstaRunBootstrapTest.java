package com.zapryan.bootstrap;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstaRunBootstrapTest {

    private static final String TARGETS = "com.zapryan.bootstrap.fixtures.TargetMethods";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    public void printsTheReturnValue() throws Throwable {
        assertEquals(0, invoke(TARGETS, "answer"));
        assertEquals("[InstaRun] Result: 42", stdout());
    }

    @Test
    public void printsNullReturnValues() throws Throwable {
        assertEquals(0, invoke(TARGETS, "nothing"));
        assertEquals("[InstaRun] Result: null", stdout());
    }

    @Test
    public void printsVoidForVoidMethods() throws Throwable {
        assertEquals(0, invoke(TARGETS, "noResult"));
        assertEquals("[InstaRun] Result: void", stdout());
    }

    @Test
    public void findsNestedClassesByBinaryName() throws Throwable {
        assertEquals(0, invoke(TARGETS + "$Nested", "name"));
        assertEquals("[InstaRun] Result: nested", stdout());
    }

    @Test
    public void invokesPrivateMethods() throws Throwable {
        assertEquals(0, invoke(TARGETS, "hidden"));
        assertEquals("[InstaRun] Result: hidden", stdout());
    }

    @Test
    public void invokesPublicMethodsOfNonPublicClasses() throws Throwable {
        assertEquals(0, invoke("com.zapryan.bootstrap.fixtures.PackagePrivateTarget", "greet"));
        assertEquals("[InstaRun] Result: hello", stdout());
    }

    @Test
    public void rethrowsTheMethodsOwnExceptionUnwrapped() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> invoke(TARGETS, "fail"));

        assertEquals("boom", thrown.getMessage());
    }

    @Test
    public void reportsAMissingClass() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invoke("com.example.Missing", "run"));
        assertTrue(stderr(), stderr().contains("Class com.example.Missing was not found"));
    }

    @Test
    public void reportsAMissingMethod() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invoke(TARGETS, "missing"));
        assertTrue(stderr(), stderr().contains(TARGETS + "#missing() was not found"));
    }

    @Test
    public void reportsAnInstanceMethod() throws Throwable {
        assertEquals(InstaRunBootstrap.EXIT_BOOTSTRAP_FAILURE, invoke(TARGETS, "instanceMethod"));
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

    private int invoke(String className, String methodName) throws Throwable {
        return run(InvocationRequestCodec.encode(new InvocationRequest(className, methodName)));
    }

    private int run(String... args) throws Throwable {
        return InstaRunBootstrap.run(args, new PrintStream(out, true), new PrintStream(err, true));
    }

    private String stdout() {
        return out.toString().trim();
    }

    private String stderr() {
        return err.toString().trim();
    }
}
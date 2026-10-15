package com.zapryan.reflection;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RunBlockerTest {

    private static final ParameterDescriptor COUNT = new ParameterDescriptor("count", "int");

    @Test
    public void staticMethodWithoutParametersHasNoBlockers() {
        assertEquals(List.of(), RunBlocker.findAll(method("com.acme.Tool", true, false)));
    }

    @Test
    public void instanceMethodIsBlocked() {
        assertEquals(List.of(RunBlocker.NOT_STATIC), RunBlocker.findAll(method("com.acme.Tool", false, false)));
    }

    @Test
    public void methodWithParametersIsBlocked() {
        assertEquals(List.of(RunBlocker.HAS_PARAMETERS),
                RunBlocker.findAll(method("com.acme.Tool", true, false, COUNT)));
    }

    @Test
    public void methodOfLocalOrAnonymousClassIsBlocked() {
        assertEquals(List.of(RunBlocker.LOCAL_OR_ANONYMOUS_CLASS), RunBlocker.findAll(method(null, true, false)));
    }

    @Test
    public void everyApplicableBlockerIsReported() {
        assertEquals(List.of(RunBlocker.LOCAL_OR_ANONYMOUS_CLASS, RunBlocker.NOT_STATIC, RunBlocker.HAS_PARAMETERS),
                RunBlocker.findAll(method(null, false, false, COUNT)));
    }

    @Test
    public void constructorIsReportedOnItsOwn() {
        assertEquals(List.of(RunBlocker.CONSTRUCTOR), RunBlocker.findAll(method("com.acme.Tool", false, true, COUNT)));
    }

    private static MethodDescriptor method(String binaryName, boolean isStatic, boolean isConstructor,
                                           ParameterDescriptor... parameters) {
        return new MethodDescriptor("com.acme.Tool", binaryName, "run", "void", isStatic, isConstructor,
                List.of(parameters));
    }
}
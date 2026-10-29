package com.zapryan.codegen;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A hand-built {@link CodeContext}: every class is referable by its binary name with dots, except the ones marked
 * hidden, and nothing is overloaded unless marked so.
 */
final class FakeContext implements CodeContext {

    private final Set<String> hidden = new HashSet<>();
    private final Set<String> overloaded = new HashSet<>();

    FakeContext hide(String classBinaryName) {
        hidden.add(classBinaryName);
        return this;
    }

    FakeContext overload(String classBinaryName, String methodName, int parameterCount) {
        overloaded.add(classBinaryName + "#" + methodName + "/" + parameterCount);
        return this;
    }

    @Override
    public @Nullable String referableName(String classBinaryName) {
        return hidden.contains(classBinaryName) ? null : classBinaryName.replace('$', '.');
    }

    @Override
    public boolean isOverloaded(String classBinaryName, String methodName, int parameterCount) {
        return overloaded.contains(classBinaryName + "#" + methodName + "/" + parameterCount);
    }

    @Override
    public TestComments comments() {
        return new TestComments() {
            @Override
            public String recordedValues() {
                return "Recorded";
            }

            @Override
            public String returnedNothing() {
                return "Returned nothing";
            }

            @Override
            public String moreValuesNotChecked() {
                return "More values";
            }

            @Override
            public String accessorThrew(String expression, String exceptionClass) {
                return expression + " threw " + exceptionClass;
            }
        };
    }
}
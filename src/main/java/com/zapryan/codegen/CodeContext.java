package com.zapryan.codegen;

import org.jetbrains.annotations.Nullable;

/** What the generator needs to know about the code around the test. In the IDE it is answered from PSI. */
public interface CodeContext {

    /** The fully qualified name to write a class by, or null if the test, in its package, can't refer to the class. */
    @Nullable String referableName(String classBinaryName);

    /**
     * Whether a class has more than one method with this name and number of parameters, or more than one such
     * constructor when the name is {@code <init>}. Literal and null arguments to it are then cast to the parameter
     * type, so the call picks the same one the run did.
     */
    boolean isOverloaded(String classBinaryName, String methodName, int parameterCount);

    /** The comments a generated test carries. */
    TestComments comments();

    interface TestComments {
        /** Above the assertions. */
        String recordedValues();

        /** Instead of assertions, for a void method. */
        String returnedNothing();

        /** Where assertions stop because there are too many values. */
        String moreValuesNotChecked();

        /** Instead of an assertion on a getter that threw while the values were recorded. */
        String accessorThrew(String expression, String exceptionClass);
    }
}
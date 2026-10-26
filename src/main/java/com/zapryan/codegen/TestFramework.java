package com.zapryan.codegen;

/** The flavour of JUnit a generated test is written for, as found on the test classpath. */
public enum TestFramework {
    JUNIT5("org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions", true, false),
    JUNIT4("org.junit.Test", "org.junit.Assert", true, true),
    /** JUnit 4 before 4.13, which has no assertThrows: an expected exception goes into the @Test annotation. */
    JUNIT4_WITHOUT_ASSERT_THROWS("org.junit.Test", "org.junit.Assert", false, true);

    private final String testAnnotation;
    private final String assertionsClass;
    private final boolean hasAssertThrows;
    private final boolean isJUnit4;

    TestFramework(String testAnnotation, String assertionsClass, boolean hasAssertThrows, boolean isJUnit4) {
        this.testAnnotation = testAnnotation;
        this.assertionsClass = assertionsClass;
        this.hasAssertThrows = hasAssertThrows;
        this.isJUnit4 = isJUnit4;
    }

    public String getTestAnnotation() {
        return testAnnotation;
    }

    /** The class the assertions are statically imported from. */
    public String getAssertionsClass() {
        return assertionsClass;
    }

    public boolean hasAssertThrows() {
        return hasAssertThrows;
    }

    /** JUnit 4 runs only public test classes and methods. */
    public boolean needsPublicMembers() {
        return isJUnit4;
    }

    /** JUnit 4's assertEquals for doubles and floats takes a tolerance; the one without it always fails. */
    public boolean needsFloatingPointDelta() {
        return isJUnit4;
    }
}
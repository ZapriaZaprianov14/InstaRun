package com.zapryan.codegen;

import com.zapryan.reflection.MethodDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * The method a test is generated for, with what the IDE knows about it when the test is written.
 *
 * @param testMethodName          the name of the test method, free in the test class
 * @param receiverTypeText        what to declare the receiver as, e.g. {@code com.acme.Cache<?, ?>}; null for a
 *                                static method
 * @param throwsCheckedExceptions whether a constructor, setter or method the test calls declares exceptions
 */
public record TestSubject(MethodDescriptor method, String testMethodName, @Nullable String receiverTypeText,
                          boolean throwsCheckedExceptions) {
}
package com.zapryan.codegen;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a generated test can do with an expression of some compile-time type: which assertions fit it and which
 * getters it can be read through. In the IDE it is answered from PSI.
 */
public interface TypeShape {

    enum Kind { PRIMITIVE, BOXED, STRING, BIG_NUMBER, ENUM, ARRAY, LIST, SET, COLLECTION, MAP, OTHER }

    Kind getKind();

    /** The type as written in source, fully qualified and with its type arguments, e.g. {@code int[]}. */
    String getText();

    /** Whether the test, in its package, can call methods on values of the type. */
    boolean isAccessible();

    /** The element type of an array or a collection; null when it isn't known, e.g. for a raw type. */
    @Nullable TypeShape getElementType();

    /** The value type of a map; null when it isn't known. */
    @Nullable TypeShape getMapValueType();

    /** The getters, or a record's accessors, that the test can call, in declaration order. */
    List<Accessor> getAccessors();

    /** A no-argument method that reads a property, e.g. {@code getName} or a record's {@code name}. */
    record Accessor(String methodName, TypeShape type) {
    }
}
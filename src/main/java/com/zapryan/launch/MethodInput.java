package com.zapryan.launch;

import com.zapryan.reflection.ValueSpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the user chose to call the method with: how to build the receiver (null for static methods) and the arguments.
 */
@Getter
@AllArgsConstructor
public final class MethodInput {

    @Nullable
    private final ValueSpec receiver;
    private final List<ValueSpec> arguments;
}
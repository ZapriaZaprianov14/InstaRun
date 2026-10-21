package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Whether and how the declaring class can be instantiated, which is what calling an instance method needs.
 */
@Getter
@AllArgsConstructor
public final class ReceiverDescriptor {

    public enum Kind { INSTANTIABLE, ABSTRACT, ENUM, INNER_CLASS }

    private final Kind kind;
    /** Empty unless the kind is {@link Kind#INSTANTIABLE}. */
    private final List<ConstructorDescriptor> constructors;

    public List<ConstructorDescriptor> getUsableConstructors() {
        return constructors.stream().filter(ConstructorDescriptor::isUsable).collect(Collectors.toList());
    }
}
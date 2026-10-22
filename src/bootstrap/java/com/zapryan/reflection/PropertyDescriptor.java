package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Something that can be given to a bean by name, through a constructor parameter or a setter. */
@Getter
@AllArgsConstructor
public final class PropertyDescriptor {

    private final String name;
    private final TypeDescriptor type;
}
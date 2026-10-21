package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public final class ConstructorDescriptor {

    private final List<ParameterDescriptor> parameters;

    public boolean isUsable() {
        return parameters.stream().allMatch(ParameterDescriptor::isSupported);
    }
}
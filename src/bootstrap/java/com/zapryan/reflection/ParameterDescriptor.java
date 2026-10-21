package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class ParameterDescriptor {
    private final String name;
    private final String typeFqn;

    public boolean isSupported() {
        return Literals.isSupported(typeFqn);
    }
}

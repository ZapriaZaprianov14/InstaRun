package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/** A one-parameter {@code setX} method of a bean, declared by the bean's class or one of its superclasses. */
@Getter
@AllArgsConstructor
public final class SetterDescriptor {

    private final String propertyName;
    private final String declaringClassBinaryName;
    private final String methodName;
    /** JVM name of the parameter type's erasure, which reflection looks the setter up by. */
    private final String erasedParameterTypeName;
    /** The parameter type as seen from the bean, i.e. with the bean's type arguments filled in. */
    private final TypeDescriptor type;

    /**
     * The property a method named like a setter sets, following the JavaBeans rules: {@code firstName} for
     * {@code setFirstName}, {@code URL} for {@code setURL}. Null for a name that isn't a setter name.
     */
    public static @Nullable String propertyNameOf(String methodName) {
        if (methodName.length() <= 3 || !methodName.startsWith("set") || !Character.isUpperCase(methodName.charAt(3))) {
            return null;
        }
        String name = methodName.substring(3);
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
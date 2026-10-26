package com.zapryan.reflection;

import java.util.Arrays;

/** Builds type descriptors by hand, the way the plugin builds them from PSI. */
public final class TestTypes {

    public static final TypeDescriptor.LiteralType STRING = literal("java.lang.String");
    public static final TypeDescriptor.LiteralType CHAR = literal("char");
    public static final TypeDescriptor.LiteralType BOOLEAN = literal("boolean");
    public static final TypeDescriptor.LiteralType BYTE = literal("byte");
    public static final TypeDescriptor.LiteralType SHORT = literal("short");
    public static final TypeDescriptor.LiteralType INT = literal("int");
    public static final TypeDescriptor.LiteralType INTEGER = literal("java.lang.Integer");
    public static final TypeDescriptor.LiteralType LONG = literal("long");
    public static final TypeDescriptor.LiteralType DOUBLE = literal("double");
    public static final TypeDescriptor.LiteralType BIG_DECIMAL = literal("java.math.BigDecimal");

    private TestTypes() {
    }

    public static TypeDescriptor.LiteralType literal(String typeName) {
        return new TypeDescriptor.LiteralType(typeName, typeName.substring(typeName.lastIndexOf('.') + 1));
    }

    /** A bean in package com.acme, still to be {@link TypeDescriptor.BeanType#define defined}. */
    public static TypeDescriptor.BeanType bean(String simpleName) {
        return new TypeDescriptor.BeanType(simpleName, "com.acme." + simpleName, "com.acme." + simpleName);
    }

    public static TypeDescriptor.EnumType enumOf(String simpleName, String... constants) {
        return new TypeDescriptor.EnumType(simpleName, "com.acme." + simpleName, "com.acme." + simpleName,
                Arrays.asList(constants));
    }

    public static TypeDescriptor.CollectionType listOf(TypeDescriptor elementType) {
        return new TypeDescriptor.CollectionType("List<" + elementType.getDisplayName() + ">",
                "java.util.List<" + elementType.getCanonicalText() + ">", elementType, "java.util.ArrayList");
    }

    public static TypeDescriptor.ArrayType arrayOf(TypeDescriptor componentType) {
        return new TypeDescriptor.ArrayType(componentType.getDisplayName() + "[]",
                componentType.getCanonicalText() + "[]", componentType, erasure(componentType));
    }

    public static TypeDescriptor.UnsupportedType unsupported(String displayName) {
        return new TypeDescriptor.UnsupportedType(displayName, "it is not supported in this test");
    }

    public static ParameterDescriptor parameter(String name, TypeDescriptor type) {
        return new ParameterDescriptor(name, type.getDisplayName(), erasure(type), type);
    }

    public static ConstructorDescriptor constructor(ParameterDescriptor... parameters) {
        return new ConstructorDescriptor(Arrays.asList(parameters));
    }

    public static SetterDescriptor setter(TypeDescriptor.BeanType bean, String property, TypeDescriptor type) {
        String methodName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        return new SetterDescriptor(property, bean.getBinaryName(), methodName, erasure(type), type);
    }

    /** The JVM name reflection would look a parameter of this type up by. */
    public static String erasure(TypeDescriptor type) {
        if (type instanceof TypeDescriptor.LiteralType) {
            return ((TypeDescriptor.LiteralType) type).getTypeName();
        }
        if (type instanceof TypeDescriptor.BeanType) {
            return ((TypeDescriptor.BeanType) type).getBinaryName();
        }
        if (type instanceof TypeDescriptor.EnumType) {
            return ((TypeDescriptor.EnumType) type).getBinaryName();
        }
        if (type instanceof TypeDescriptor.ArrayType) {
            return ((TypeDescriptor.ArrayType) type).getComponentTypeName() + "[]";
        }
        if (type instanceof TypeDescriptor.CollectionType) {
            return "java.util.List";
        }
        return "java.util.Map";
    }
}
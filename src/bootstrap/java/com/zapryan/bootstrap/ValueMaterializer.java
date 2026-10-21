package com.zapryan.bootstrap;

import com.zapryan.reflection.ValueSpec;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns {@link ValueSpec} recipes and type names into real classes and objects, using the launched JVM's classpath.
 */
final class ValueMaterializer {

    private static final Map<String, Class<?>> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put("boolean", boolean.class);
        PRIMITIVES.put("byte", byte.class);
        PRIMITIVES.put("char", char.class);
        PRIMITIVES.put("short", short.class);
        PRIMITIVES.put("int", int.class);
        PRIMITIVES.put("long", long.class);
        PRIMITIVES.put("float", float.class);
        PRIMITIVES.put("double", double.class);
    }

    private final ClassLoader classLoader;

    ValueMaterializer(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    Class<?> loadClass(String binaryName) throws BootstrapFailure {
        try {
            return Class.forName(binaryName, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new BootstrapFailure("Class " + binaryName + " was not found on the classpath.");
        } catch (LinkageError e) {
            throw new BootstrapFailure("Class " + binaryName + " could not be loaded: " + e);
        }
    }

    Class<?>[] resolveTypes(List<String> typeNames) throws BootstrapFailure {
        Class<?>[] types = new Class<?>[typeNames.size()];
        for (int i = 0; i < types.length; i++) {
            Class<?> primitive = PRIMITIVES.get(typeNames.get(i));
            types[i] = primitive != null ? primitive : loadClass(typeNames.get(i));
        }
        return types;
    }

    Object[] materializeAll(List<ValueSpec> specs) throws BootstrapFailure, ReflectiveOperationException {
        Object[] values = new Object[specs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = materialize(specs.get(i));
        }
        return values;
    }

    Object materialize(ValueSpec spec) throws BootstrapFailure, ReflectiveOperationException {
        if (spec instanceof ValueSpec.Literal) {
            return ((ValueSpec.Literal) spec).getValue();
        }
        if (spec instanceof ValueSpec.Null) {
            return null;
        }
        if (spec instanceof ValueSpec.Constructed) {
            return construct((ValueSpec.Constructed) spec);
        }
        throw new BootstrapFailure("Values of kind " + spec.getClass().getSimpleName() + " cannot be created by reflection.");
    }

    private Object construct(ValueSpec.Constructed spec) throws BootstrapFailure, ReflectiveOperationException {
        Class<?> type = loadClass(spec.getClassBinaryName());
        String label = signature(type.getName(), spec.getParameterTypeNames());
        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor(resolveTypes(spec.getParameterTypeNames()));
        } catch (NoSuchMethodException e) {
            throw new BootstrapFailure(
                    "Constructor " + label + " was not found; the compiled class may be out of date.");
        } catch (LinkageError e) {
            throw new BootstrapFailure("The constructors of " + type.getName() + " could not be read: " + e);
        }
        makeAccessible(constructor, "Constructor " + label);
        return constructor.newInstance(materializeAll(spec.getArguments()));
    }

    static void makeAccessible(AccessibleObject member, String label) throws BootstrapFailure {
        try {
            member.setAccessible(true);
        } catch (RuntimeException e) {
            throw new BootstrapFailure(label + " could not be made accessible: " + e);
        }
    }

    static String signature(String name, List<String> parameterTypeNames) {
        return name + "(" + String.join(", ", parameterTypeNames) + ")";
    }
}
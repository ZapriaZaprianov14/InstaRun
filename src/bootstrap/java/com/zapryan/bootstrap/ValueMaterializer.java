package com.zapryan.bootstrap;

import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.ValueSpec;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
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

    /** Resolves a primitive, class or array type name, e.g. {@code int}, {@code a.Outer$Inner} or {@code int[][]}. */
    Class<?> resolveType(String typeName) throws BootstrapFailure {
        if (typeName.endsWith("[]")) {
            Class<?> componentType = resolveType(typeName.substring(0, typeName.length() - 2));
            return Array.newInstance(componentType, 0).getClass();
        }
        Class<?> primitive = PRIMITIVES.get(typeName);
        return primitive != null ? primitive : loadClass(typeName);
    }

    Class<?>[] resolveTypes(List<String> typeNames) throws BootstrapFailure {
        Class<?>[] types = new Class<?>[typeNames.size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = resolveType(typeNames.get(i));
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
        if (spec instanceof ValueSpec.ArrayOf) {
            return array((ValueSpec.ArrayOf) spec);
        }
        if (spec instanceof ValueSpec.CollectionOf) {
            return collection((ValueSpec.CollectionOf) spec);
        }
        if (spec instanceof ValueSpec.EnumConstant) {
            return enumConstant((ValueSpec.EnumConstant) spec);
        }
        throw new BootstrapFailure(
                "Values of kind " + spec.getClass().getSimpleName() + " cannot be created by reflection.");
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
        Object instance = constructor.newInstance(materializeAll(spec.getArguments()));
        for (SetterCall call : spec.getSetterCalls()) {
            callSetter(instance, call);
        }
        return instance;
    }

    private void callSetter(Object instance, SetterCall call) throws BootstrapFailure, ReflectiveOperationException {
        Class<?> declaringClass = loadClass(call.getDeclaringClassBinaryName());
        String label = signature(declaringClass.getName() + "#" + call.getMethodName(),
                Collections.singletonList(call.getParameterTypeName()));
        Method setter;
        try {
            setter = declaringClass.getDeclaredMethod(call.getMethodName(), resolveType(call.getParameterTypeName()));
        } catch (NoSuchMethodException e) {
            throw new BootstrapFailure("Setter " + label + " was not found; the compiled class may be out of date.");
        } catch (LinkageError e) {
            throw new BootstrapFailure("The methods of " + declaringClass.getName() + " could not be read: " + e);
        }
        makeAccessible(setter, "Setter " + label);
        setter.invoke(instance, materialize(call.getValue()));
    }

    private Object array(ValueSpec.ArrayOf spec) throws BootstrapFailure, ReflectiveOperationException {
        Class<?> componentType = resolveType(spec.getComponentTypeName());
        List<ValueSpec> elements = spec.getElements();
        Object array = Array.newInstance(componentType, elements.size());
        for (int i = 0; i < elements.size(); i++) {
            Object element = materialize(elements.get(i));
            try {
                Array.set(array, i, element);
            } catch (IllegalArgumentException e) {
                throw new BootstrapFailure("The element at index " + i + " cannot be stored in an array of "
                        + componentType.getName() + ": " + e);
            }
        }
        return array;
    }

    private Object collection(ValueSpec.CollectionOf spec) throws BootstrapFailure, ReflectiveOperationException {
        Class<?> type = loadClass(spec.getCollectionClassBinaryName());
        if (!Collection.class.isAssignableFrom(type)) {
            throw new BootstrapFailure(type.getName() + " is not a collection.");
        }
        @SuppressWarnings("unchecked")
        Collection<Object> collection = (Collection<Object>) type.getConstructor().newInstance();
        List<ValueSpec> elements = spec.getElements();
        for (int i = 0; i < elements.size(); i++) {
            Object element = materialize(elements.get(i));
            try {
                collection.add(element);
            } catch (RuntimeException e) {
                // e.g. a TreeSet refusing elements that aren't Comparable
                throw new BootstrapFailure("The element at index " + i + " could not be added to a " + type.getName()
                        + ": " + e);
            }
        }
        return collection;
    }

    private Object enumConstant(ValueSpec.EnumConstant spec) throws BootstrapFailure {
        Class<?> type = loadClass(spec.getEnumClassBinaryName());
        if (!type.isEnum()) {
            throw new BootstrapFailure(type.getName() + " is not an enum; the compiled class may be out of date.");
        }
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(spec.getConstantName())) {
                return constant;
            }
        }
        throw new BootstrapFailure("Enum " + type.getName() + " has no constant " + spec.getConstantName()
                + "; the compiled class may be out of date.");
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
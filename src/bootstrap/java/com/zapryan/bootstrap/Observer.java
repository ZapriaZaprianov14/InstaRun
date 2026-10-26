package com.zapryan.bootstrap;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records what a returned value looks like: literals and enum constants as they are, arrays, collections and maps
 * element by element, JDK objects by their toString, and any other object by calling its getters, or a record's
 * accessors. Nesting, collection sizes and the total are capped, so a large or self-referencing graph stays small.
 */
final class Observer {

    /** Objects nested deeper than this are not looked into: the result itself is at depth 0. */
    static final int MAX_DEPTH = 3;
    static final int MAX_ELEMENTS = 20;
    static final int MAX_VALUES = 500;

    private static final Set<Class<?>> LITERAL_CLASSES = new HashSet<>(Arrays.<Class<?>>asList(
            String.class, Character.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, BigInteger.class, BigDecimal.class));

    /** The objects being recorded around the current one, to recognize an object that contains itself. */
    private final Map<Object, Boolean> enclosing = new IdentityHashMap<>();
    private int recorded;

    Observation observe(@Nullable Object value) {
        return observe(value, 0);
    }

    private Observation observe(@Nullable Object value, int depth) {
        if (value == null) {
            return new Observation.Null();
        }
        if (recorded >= MAX_VALUES) {
            return unrecorded(Observation.Unrecorded.Reason.LIMIT_REACHED, null);
        }
        recorded++;
        if (LITERAL_CLASSES.contains(value.getClass())) {
            return new Observation.Literal((Serializable) value);
        }
        if (value instanceof Enum) {
            Enum<?> constant = (Enum<?>) value;
            return new Observation.EnumConstant(constant.getDeclaringClass().getName(), constant.name());
        }
        if (enclosing.containsKey(value)) {
            return unrecorded(Observation.Unrecorded.Reason.CYCLE, null);
        }
        if (depth >= MAX_DEPTH) {
            return unrecorded(Observation.Unrecorded.Reason.TOO_DEEP, null);
        }
        enclosing.put(value, Boolean.TRUE);
        try {
            return observeObject(value, depth);
        } catch (RuntimeException e) {
            // e.g. a lazily loaded collection that can no longer load, or a toString that fails
            return unrecorded(Observation.Unrecorded.Reason.THREW, e.getClass().getName());
        } finally {
            enclosing.remove(value);
        }
    }

    private Observation observeObject(Object value, int depth) {
        if (value.getClass().isArray()) {
            return array(value, depth);
        }
        if (value instanceof Collection) {
            return collection((Collection<?>) value, depth);
        }
        if (value instanceof Map) {
            return map((Map<?, ?>) value, depth);
        }
        if (value instanceof Throwable) {
            Map<String, Observation> properties = new LinkedHashMap<>();
            properties.put("getMessage", observe(((Throwable) value).getMessage(), depth + 1));
            return new Observation.Bean(value.getClass().getName(), properties);
        }
        if (isFromJdk(value.getClass())) {
            return overridesToString(value.getClass())
                    ? new Observation.Text(value.getClass().getName(), value.toString())
                    : unrecorded(Observation.Unrecorded.Reason.OPAQUE, null);
        }
        return bean(value, depth);
    }

    private Observation array(Object array, int depth) {
        int length = Array.getLength(array);
        List<Observation> elements = new ArrayList<>();
        for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
            elements.add(observe(Array.get(array, i), depth + 1));
        }
        return new Observation.Sequence(Observation.Sequence.Kind.ARRAY, length, elements);
    }

    private Observation collection(Collection<?> collection, int depth) {
        Observation.Sequence.Kind kind = collection instanceof List ? Observation.Sequence.Kind.LIST
                : collection instanceof Set ? Observation.Sequence.Kind.SET
                : Observation.Sequence.Kind.OTHER_COLLECTION;
        List<Observation> elements = new ArrayList<>();
        Iterator<?> iterator = collection.iterator();
        while (iterator.hasNext() && elements.size() < MAX_ELEMENTS) {
            elements.add(observe(iterator.next(), depth + 1));
        }
        return new Observation.Sequence(kind, collection.size(), elements);
    }

    private Observation map(Map<?, ?> map, int depth) {
        List<Observation.Entry> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entries.size() == MAX_ELEMENTS) {
                break;
            }
            entries.add(new Observation.Entry(observe(entry.getKey(), depth + 1), observe(entry.getValue(), depth + 1)));
        }
        return new Observation.MapOf(map.size(), entries);
    }

    private Observation bean(Object value, int depth) {
        Map<String, Observation> properties = new LinkedHashMap<>();
        for (Method accessor : accessorsOf(value.getClass())) {
            properties.put(accessor.getName(), property(value, accessor, depth));
        }
        return new Observation.Bean(value.getClass().getName(), properties);
    }

    private Observation property(Object value, Method accessor, int depth) {
        try {
            if (!Modifier.isPublic(accessor.getDeclaringClass().getModifiers())) {
                // A public method of a non-public class, e.g. a private class implementing a public interface.
                accessor.setAccessible(true);
            }
            return observe(accessor.invoke(value), depth + 1);
        } catch (InvocationTargetException e) {
            return unrecorded(Observation.Unrecorded.Reason.THREW, e.getCause().getClass().getName());
        } catch (IllegalAccessException | RuntimeException e) {
            return unrecorded(Observation.Unrecorded.Reason.OPAQUE, null);
        }
    }

    /** A record's accessors in declaration order, or else the public getters sorted by name. */
    static List<Method> accessorsOf(Class<?> type) {
        List<Method> recordAccessors = recordAccessorsOf(type);
        if (recordAccessors != null) {
            return recordAccessors;
        }
        List<Method> getters = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (isGetter(method)) {
                getters.add(method);
            }
        }
        getters.sort(Comparator.comparing(Method::getName));
        return getters;
    }

    static boolean isGetter(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                || method.getParameterTypes().length != 0) {
            return false;
        }
        String name = method.getName();
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || name.equals("getClass")) {
            return false;
        }
        if (startsWithUppercaseAfter(name, "get")) {
            return true;
        }
        return startsWithUppercaseAfter(name, "is") && (returnType == boolean.class || returnType == Boolean.class);
    }

    /** Null on Java 8 to 15, which have no records, and for classes that aren't records. */
    private static @Nullable List<Method> recordAccessorsOf(Class<?> type) {
        try {
            if (!(Boolean) Class.class.getMethod("isRecord").invoke(type)) {
                return null;
            }
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            List<Method> accessors = new ArrayList<>(components.length);
            for (Object component : components) {
                accessors.add((Method) component.getClass().getMethod("getAccessor").invoke(component));
            }
            return accessors;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean startsWithUppercaseAfter(String name, String prefix) {
        return name.length() > prefix.length() && name.startsWith(prefix)
                && Character.isUpperCase(name.charAt(prefix.length()));
    }

    private static boolean isFromJdk(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("com.sun.");
    }

    private static boolean overridesToString(Class<?> type) {
        try {
            return type.getMethod("toString").getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Observation unrecorded(Observation.Unrecorded.Reason reason, @Nullable String detail) {
        return new Observation.Unrecorded(reason, detail);
    }
}
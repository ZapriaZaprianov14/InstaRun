package com.zapryan.bootstrap;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Records what a returned value looks like to a test that reads it through its declared type: literals and enum
 * constants as they are, arrays, collections and maps element by element, JDK objects by their toString, and any
 * other object through the getters, or a record's accessors, of its declared type. A generated test lives in the
 * package of the class it tests and extends nothing, so that package decides which classes and getters it can use.
 * Nesting, collection sizes and the total are capped, so a large or self-referencing graph stays small.
 */
final class Observer {

    /** Objects nested deeper than this are not looked into: the result itself is at depth 0. */
    static final int MAX_DEPTH = 3;
    static final int MAX_ELEMENTS = 20;
    static final int MAX_VALUES = 500;

    private static final Set<Class<?>> LITERAL_CLASSES = new HashSet<>(Arrays.<Class<?>>asList(
            String.class, Character.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, BigInteger.class, BigDecimal.class));

    private final String testPackage;
    /** The objects being recorded around the current one, to recognize an object that contains itself. */
    private final Map<Object, Boolean> enclosing = new IdentityHashMap<>();
    private int recorded;

    Observer(String testPackage) {
        this.testPackage = testPackage;
    }

    /** The package a test of the class is written in: the class's own. */
    static String packageOf(Class<?> type) {
        String name = type.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }

    /**
     * What a test can declare a value of the type as: the type itself, or its erasure when it mentions type
     * variables or classes the test can't see, or else Object.
     */
    Type declarable(Type type) {
        if (isWritable(type)) {
            return type;
        }
        Class<?> erasure = rawOf(type);
        return isVisible(erasure, testPackage) ? erasure : Object.class;
    }

    /** The class's name as the test writes it, or null if the test can't see the class. */
    static @Nullable String nameInTest(Class<?> type, String testPackage) {
        return isVisible(type, testPackage) ? type.getCanonicalName() : null;
    }

    /** The type as written in source, with fully qualified class names, e.g. {@code java.util.List<a.Item>}. */
    static String typeText(Type type) {
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            StringBuilder text = new StringBuilder(typeText(((ParameterizedType) type).getRawType()));
            for (int i = 0; i < arguments.length; i++) {
                text.append(i == 0 ? "<" : ", ").append(typeText(arguments[i]));
            }
            return arguments.length == 0 ? text.toString() : text.append('>').toString();
        }
        if (type instanceof GenericArrayType) {
            return typeText(((GenericArrayType) type).getGenericComponentType()) + "[]";
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            if (wildcard.getLowerBounds().length > 0) {
                return "? super " + typeText(wildcard.getLowerBounds()[0]);
            }
            Type bound = wildcard.getUpperBounds()[0];
            return bound == Object.class ? "?" : "? extends " + typeText(bound);
        }
        return type instanceof Class ? ((Class<?>) type).getCanonicalName() : type.getTypeName();
    }

    /** @param declared what the test declares the value as, a type {@link #declarable} returned */
    Observation observe(@Nullable Object value, Type declared) {
        return observe(value, declared, 0);
    }

    private Observation observe(@Nullable Object value, Type declared, int depth) {
        if (value == null) {
            return new Observation.Null();
        }
        if (recorded >= MAX_VALUES) {
            return unrecorded(Observation.Unrecorded.Reason.LIMIT_REACHED, null);
        }
        recorded++;
        if (LITERAL_CLASSES.contains(value.getClass())) {
            return new Observation.Literal((Serializable) value, rawOf(declared).isPrimitive());
        }
        if (value instanceof Enum) {
            Enum<?> constant = (Enum<?>) value;
            return new Observation.EnumConstant(nameInTest(constant.getDeclaringClass(), testPackage), constant.name());
        }
        if (enclosing.containsKey(value)) {
            return unrecorded(Observation.Unrecorded.Reason.CYCLE, null);
        }
        if (depth >= MAX_DEPTH) {
            return unrecorded(Observation.Unrecorded.Reason.TOO_DEEP, null);
        }
        enclosing.put(value, Boolean.TRUE);
        try {
            return observeObject(value, declared, depth);
        } catch (RuntimeException e) {
            // e.g. a lazily loaded collection that can no longer load, or a toString that fails
            return unrecorded(Observation.Unrecorded.Reason.THREW, e.getClass().getName());
        } finally {
            enclosing.remove(value);
        }
    }

    /** Values the test can only reach through methods of their declared type are recorded only if it can see it. */
    private Observation observeObject(Object value, Type declared, int depth) {
        Class<?> declaredClass = rawOf(declared);
        boolean readable = isVisible(declaredClass, testPackage);
        if (value.getClass().isArray()) {
            return readable && declaredClass.isArray() ? array(value, declared, depth) : opaque();
        }
        if (value instanceof Collection) {
            return readable && Collection.class.isAssignableFrom(declaredClass)
                    ? collection((Collection<?>) value, declared, declaredClass, depth)
                    : opaque();
        }
        if (value instanceof Map) {
            return readable && Map.class.isAssignableFrom(declaredClass)
                    ? map((Map<?, ?>) value, declared, depth)
                    : opaque();
        }
        if (value instanceof Throwable) {
            if (!readable || !Throwable.class.isAssignableFrom(declaredClass)) {
                return opaque();
            }
            Map<String, Observation> properties = new LinkedHashMap<>();
            properties.put("getMessage", observe(((Throwable) value).getMessage(), String.class, depth + 1));
            return new Observation.Bean(value.getClass().getName(), properties);
        }
        if (isFromJdk(value.getClass())) {
            return overridesToString(value.getClass())
                    ? new Observation.Text(value.getClass().getName(), value.toString())
                    : opaque();
        }
        return readable ? bean(value, declared, declaredClass, depth) : opaque();
    }

    private Observation array(Object array, Type declared, int depth) {
        Type elementType = declared instanceof GenericArrayType
                ? ((GenericArrayType) declared).getGenericComponentType()
                : rawOf(declared).getComponentType();
        Class<?> elementClass = rawOf(elementType);
        boolean literalElements = elementClass.isPrimitive() || LITERAL_CLASSES.contains(elementClass)
                || elementClass.isEnum() && isVisible(elementClass, testPackage);
        int length = Array.getLength(array);
        List<Observation> elements = new ArrayList<>();
        for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
            elements.add(observe(Array.get(array, i), elementType, depth + 1));
        }
        return new Observation.Sequence(Observation.Sequence.Kind.ARRAY, length, elements,
                literalElements ? elementClass.getCanonicalName() : null);
    }

    private Observation collection(Collection<?> collection, Type declared, Class<?> declaredClass, int depth) {
        Observation.Sequence.Kind kind = collection instanceof Set ? Observation.Sequence.Kind.SET
                : collection instanceof List && List.class.isAssignableFrom(declaredClass)
                ? Observation.Sequence.Kind.LIST
                : Observation.Sequence.Kind.OTHER_COLLECTION;
        Type elementType = typeArgument(declared, Collection.class, 0);
        List<Observation> elements = new ArrayList<>();
        Iterator<?> iterator = collection.iterator();
        while (iterator.hasNext() && elements.size() < MAX_ELEMENTS) {
            elements.add(observe(iterator.next(), elementType, depth + 1));
        }
        return new Observation.Sequence(kind, collection.size(), elements, null);
    }

    private Observation map(Map<?, ?> map, Type declared, int depth) {
        Type keyType = typeArgument(declared, Map.class, 0);
        Type valueType = typeArgument(declared, Map.class, 1);
        List<Observation.Entry> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entries.size() == MAX_ELEMENTS) {
                break;
            }
            entries.add(new Observation.Entry(observe(entry.getKey(), keyType, depth + 1),
                    observe(entry.getValue(), valueType, depth + 1)));
        }
        return new Observation.MapOf(map.size(), entries);
    }

    private Observation bean(Object value, Type declared, Class<?> declaredClass, int depth) {
        List<Method> accessors = accessorsOf(declaredClass);
        if (accessors.isEmpty()) {
            return opaque();
        }
        Map<String, Observation> properties = new LinkedHashMap<>();
        for (Method accessor : accessors) {
            properties.put(accessor.getName(), property(value, accessor, returnTypeOf(accessor, declared), depth));
        }
        return new Observation.Bean(value.getClass().getName(), properties);
    }

    private Observation property(Object value, Method accessor, Type declared, int depth) {
        try {
            if (!Modifier.isPublic(accessor.getDeclaringClass().getModifiers())) {
                // A public method of a non-public class, e.g. one a public class inherits from a package-private one.
                accessor.setAccessible(true);
            }
            return observe(accessor.invoke(value), declared, depth + 1);
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
        // An interface can list a getter twice, as declared and as narrowed by a subinterface; the narrower one wins.
        Map<String, Method> getters = new TreeMap<>();
        for (Method method : type.getMethods()) {
            Method other = getters.get(method.getName());
            if (isGetter(method)
                    && (other == null || other.getReturnType().isAssignableFrom(method.getReturnType()))) {
                getters.put(method.getName(), method);
            }
        }
        return new ArrayList<>(getters.values());
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

    /** An accessor's return type as the declared type sees it, e.g. String for {@code T getValue()} of a Box<String>. */
    private static Type returnTypeOf(Method accessor, Type declared) {
        Type type = accessor.getGenericReturnType();
        if (type instanceof TypeVariable && ((TypeVariable<?>) type).getGenericDeclaration() instanceof Class) {
            Class<?> owner = (Class<?>) ((TypeVariable<?>) type).getGenericDeclaration();
            return typeArgument(declared, owner, Arrays.asList(owner.getTypeParameters()).indexOf(type));
        }
        return type;
    }

    /**
     * The type argument the declared type gives a type parameter of a class it extends or implements, e.g. String
     * for the element type of an {@code ArrayList<String>} as a Collection. When the declared type doesn't say, e.g.
     * because it is raw, this is the type parameter itself, which the test sees as its bound.
     */
    static Type typeArgument(Type declared, Class<?> owner, int index) {
        Type argument = typeArgument(boundOf(declared), owner, index, Collections.<TypeVariable<?>, Type>emptyMap());
        return argument != null ? argument : owner.getTypeParameters()[index];
    }

    private static @Nullable Type typeArgument(Type type, Class<?> owner, int index,
                                               Map<TypeVariable<?>, Type> outer) {
        Class<?> raw = rawOf(type);
        if (!owner.isAssignableFrom(raw)) {
            return null;
        }
        TypeVariable<?>[] parameters = raw.getTypeParameters();
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            for (int i = 0; i < parameters.length; i++) {
                bindings.put(parameters[i], outer.containsKey(arguments[i]) ? outer.get(arguments[i]) : arguments[i]);
            }
        }
        if (raw == owner) {
            return bindings.get(parameters[index]);
        }
        List<Type> supertypes = new ArrayList<>(Arrays.asList(raw.getGenericInterfaces()));
        if (raw.getGenericSuperclass() != null) {
            supertypes.add(raw.getGenericSuperclass());
        }
        for (Type supertype : supertypes) {
            Type argument = typeArgument(supertype, owner, index, bindings);
            if (argument != null) {
                return argument;
            }
        }
        return null;
    }

    /** A type variable or wildcard as its first upper bound, which is all a test knows of its values. */
    private static Type boundOf(Type type) {
        if (type instanceof TypeVariable) {
            return boundOf(((TypeVariable<?>) type).getBounds()[0]);
        }
        if (type instanceof WildcardType) {
            return boundOf(((WildcardType) type).getUpperBounds()[0]);
        }
        return type;
    }

    /** The class of a type's values as a test sees them, i.e. its erasure. */
    static Class<?> rawOf(Type type) {
        Type bound = boundOf(type);
        if (bound instanceof Class) {
            return (Class<?>) bound;
        }
        if (bound instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) bound).getRawType();
        }
        if (bound instanceof GenericArrayType) {
            return Array.newInstance(rawOf(((GenericArrayType) bound).getGenericComponentType()), 0).getClass();
        }
        return Object.class;
    }

    /** Whether the test can write the type: no type variables, and only classes it can see. */
    private boolean isWritable(Type type) {
        if (type instanceof Class) {
            return isVisible((Class<?>) type, testPackage);
        }
        if (type instanceof GenericArrayType) {
            return isWritable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            return wildcard.getLowerBounds().length > 0 ? isWritable(wildcard.getLowerBounds()[0])
                    : isWritable(wildcard.getUpperBounds()[0]);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            // An inner class of a generic class would need its outer class's type arguments written too.
            if (parameterized.getOwnerType() instanceof ParameterizedType
                    || !isWritable(parameterized.getRawType())) {
                return false;
            }
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (!isWritable(argument)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Whether a test in the package can use the class: public classes, and in its own package anything not private,
     * all the way out through the classes it is nested in.
     */
    private static boolean isVisible(Class<?> type, String testPackage) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (type.isPrimitive()) {
            return true;
        }
        if (type.isAnonymousClass() || type.isLocalClass() || type.isSynthetic()) {
            return false;
        }
        for (Class<?> current = type; current != null; current = current.getDeclaringClass()) {
            int modifiers = current.getModifiers();
            if (Modifier.isPrivate(modifiers)
                    || !Modifier.isPublic(modifiers) && !packageOf(current).equals(testPackage)) {
                return false;
            }
        }
        return true;
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

    private static Observation opaque() {
        return unrecorded(Observation.Unrecorded.Reason.OPAQUE, null);
    }

    private static Observation unrecorded(Observation.Unrecorded.Reason reason, @Nullable String detail) {
        return new Observation.Unrecorded(reason, detail);
    }
}
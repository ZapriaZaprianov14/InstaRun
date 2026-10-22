package com.zapryan.reflection;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What InstaRun knows about a declared type: whether a value of it can be entered and how that value gets built.
 * Beans can refer to themselves, directly or through other beans, so the descriptors of a method form a graph.
 */
public abstract class TypeDescriptor {

    private TypeDescriptor() {
    }

    /** The type as written in source with simple class names, e.g. {@code List<Address>}. */
    public abstract String getDisplayName();

    public boolean isSupported() {
        return getUnsupportedCause() == null;
    }

    /**
     * The type that keeps a value of this one from being entered: an {@link UnsupportedType}, or a {@link BeanType}
     * without a usable constructor. Null when a value can be entered.
     */
    public @Nullable TypeDescriptor getUnsupportedCause() {
        return null;
    }

    /** Whether null is a valid value, which it is for every type but the primitives. */
    public boolean isNullable() {
        return true;
    }

    /**
     * Marks every bean that has no constructor whose parameters can all be entered as unsupported. A bean's support
     * depends on other beans and cycles are allowed, so this repeats until nothing changes; call it once every bean
     * of a graph is defined.
     */
    public static void settle(Collection<BeanType> beans) {
        boolean changed;
        do {
            changed = false;
            for (BeanType bean : beans) {
                if (bean.supported && bean.getUsableConstructors().isEmpty()) {
                    bean.supported = false;
                    changed = true;
                }
            }
        } while (changed);
    }

    /** A primitive, a wrapper, String, BigInteger or BigDecimal. */
    @Getter
    public static final class LiteralType extends TypeDescriptor {
        /** The name {@link Literals} knows the type by, e.g. {@code int} or {@code java.lang.String}. */
        private final String typeName;
        private final String displayName;

        public LiteralType(String typeName, String displayName) {
            if (!Literals.isSupported(typeName)) {
                throw new IllegalArgumentException("Not a literal type: " + typeName);
            }
            this.typeName = typeName;
            this.displayName = displayName;
        }

        public Literals.Kind getKind() {
            return Literals.kindOf(typeName);
        }

        @Override
        public boolean isNullable() {
            return Literals.isNullable(typeName);
        }
    }

    @Getter
    public static final class EnumType extends TypeDescriptor {
        private final String displayName;
        private final String binaryName;
        /** Constant names in declaration order. */
        private final List<String> constants;

        public EnumType(String displayName, String binaryName, List<String> constants) {
            this.displayName = displayName;
            this.binaryName = binaryName;
            this.constants = new ArrayList<>(constants);
        }
    }

    /** A collection or an array: elements of one type, in order. */
    @Getter
    public abstract static class SequenceType extends TypeDescriptor {
        private final String displayName;
        private final TypeDescriptor elementType;

        private SequenceType(String displayName, TypeDescriptor elementType) {
            this.displayName = displayName;
            this.elementType = elementType;
        }

        @Override
        public @Nullable TypeDescriptor getUnsupportedCause() {
            return elementType.getUnsupportedCause();
        }
    }

    @Getter
    public static final class CollectionType extends SequenceType {
        private static final Map<String, String> IMPLEMENTATIONS = new HashMap<>();

        static {
            IMPLEMENTATIONS.put("java.lang.Iterable", "java.util.ArrayList");
            IMPLEMENTATIONS.put("java.util.Collection", "java.util.ArrayList");
            IMPLEMENTATIONS.put("java.util.List", "java.util.ArrayList");
            IMPLEMENTATIONS.put("java.util.ArrayList", "java.util.ArrayList");
            IMPLEMENTATIONS.put("java.util.LinkedList", "java.util.LinkedList");
            IMPLEMENTATIONS.put("java.util.Set", "java.util.LinkedHashSet");
            IMPLEMENTATIONS.put("java.util.HashSet", "java.util.HashSet");
            IMPLEMENTATIONS.put("java.util.LinkedHashSet", "java.util.LinkedHashSet");
            IMPLEMENTATIONS.put("java.util.SortedSet", "java.util.TreeSet");
            IMPLEMENTATIONS.put("java.util.NavigableSet", "java.util.TreeSet");
            IMPLEMENTATIONS.put("java.util.TreeSet", "java.util.TreeSet");
            IMPLEMENTATIONS.put("java.util.Queue", "java.util.LinkedList");
            IMPLEMENTATIONS.put("java.util.Deque", "java.util.LinkedList");
            IMPLEMENTATIONS.put("java.util.ArrayDeque", "java.util.ArrayDeque");
        }

        /** The class instantiated for the declared type, e.g. {@code java.util.ArrayList} for a {@code List}. */
        private final String implementationClassName;

        public CollectionType(String displayName, TypeDescriptor elementType, String implementationClassName) {
            super(displayName, elementType);
            this.implementationClassName = implementationClassName;
        }

        /**
         * The class to instantiate for a declared collection type, keeping the order the elements were entered in
         * wherever the declared type allows it; null for a collection type that is not supported.
         */
        public static @Nullable String implementationOf(String declaredClassName) {
            return IMPLEMENTATIONS.get(declaredClassName);
        }
    }

    @Getter
    public static final class ArrayType extends SequenceType {
        /** JVM name of the component type's erasure, e.g. {@code int}, {@code com.acme.Order} or {@code int[]}. */
        private final String componentTypeName;

        public ArrayType(String displayName, TypeDescriptor componentType, String componentTypeName) {
            super(displayName, componentType);
            this.componentTypeName = componentTypeName;
        }
    }

    /**
     * A class whose values are built by one of its constructors, plus setters for the properties that constructor
     * doesn't take. Created empty and {@link #define defined} afterwards, so its members can refer back to it.
     */
    public static final class BeanType extends TypeDescriptor {
        @Getter
        private final String displayName;
        @Getter
        private final String binaryName;
        @Getter
        private List<ConstructorDescriptor> constructors = Collections.emptyList();
        @Getter
        private List<SetterDescriptor> setters = Collections.emptyList();
        private boolean defined;
        private boolean supported = true;

        public BeanType(String displayName, String binaryName) {
            this.displayName = displayName;
            this.binaryName = binaryName;
        }

        public void define(List<ConstructorDescriptor> constructors, List<SetterDescriptor> setters) {
            if (defined) {
                throw new IllegalStateException(displayName + " is already defined");
            }
            this.constructors = new ArrayList<>(constructors);
            this.setters = new ArrayList<>(setters);
            defined = true;
        }

        @Override
        public @Nullable TypeDescriptor getUnsupportedCause() {
            return supported ? null : this;
        }

        /** Constructors whose parameters can all be entered. */
        public List<ConstructorDescriptor> getUsableConstructors() {
            List<ConstructorDescriptor> usable = new ArrayList<>();
            for (ConstructorDescriptor constructor : constructors) {
                if (constructor.isUsable()) {
                    usable.add(constructor);
                }
            }
            return usable;
        }

        public @Nullable SetterDescriptor findSetter(String propertyName) {
            for (SetterDescriptor setter : setters) {
                if (setter.getPropertyName().equals(propertyName)) {
                    return setter;
                }
            }
            return null;
        }

        /**
         * Everything that can be given to the bean: the parameters of its usable constructors, widest constructor
         * first, then its setters. Properties of a type that can't be entered are included so they can be reported.
         */
        public List<PropertyDescriptor> getProperties() {
            List<ConstructorDescriptor> widestFirst = getUsableConstructors();
            Collections.sort(widestFirst, (a, b) -> b.getParameters().size() - a.getParameters().size());
            Map<String, PropertyDescriptor> properties = new LinkedHashMap<>();
            for (ConstructorDescriptor constructor : widestFirst) {
                for (ParameterDescriptor parameter : constructor.getParameters()) {
                    if (!properties.containsKey(parameter.getName())) {
                        properties.put(parameter.getName(),
                                new PropertyDescriptor(parameter.getName(), parameter.getType()));
                    }
                }
            }
            for (SetterDescriptor setter : setters) {
                if (!properties.containsKey(setter.getPropertyName())) {
                    properties.put(setter.getPropertyName(),
                            new PropertyDescriptor(setter.getPropertyName(), setter.getType()));
                }
            }
            return new ArrayList<>(properties.values());
        }
    }

    /** A type no value can be entered for yet, with the reason shown to the user. */
    @Getter
    public static final class UnsupportedType extends TypeDescriptor {
        private final String displayName;
        private final String reason;

        public UnsupportedType(String displayName, String reason) {
            this.displayName = displayName;
            this.reason = reason;
        }

        @Override
        public @Nullable TypeDescriptor getUnsupportedCause() {
            return this;
        }
    }
}
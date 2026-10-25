package com.zapryan.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Whether and how the declaring class can be instantiated, which is what calling an instance method needs.
 */
@Getter
@AllArgsConstructor
public final class ReceiverDescriptor {

    public enum Kind {
        INSTANTIABLE,
        ABSTRACT,
        ENUM,
        INNER_CLASS,
        /** Managed by Spring: created with {@code new}, its injected dependencies would be null. */
        SPRING_BEAN
    }

    private final Kind kind;
    /** Empty unless the kind is {@link Kind#INSTANTIABLE}. */
    private final List<ConstructorDescriptor> constructors;
    /** For {@link Kind#SPRING_BEAN}, the annotation that shows it, e.g. {@code @Service} or {@code @Autowired}. */
    private final @Nullable String springEvidence;

    public ReceiverDescriptor(Kind kind, List<ConstructorDescriptor> constructors) {
        this(kind, constructors, null);
    }

    public List<ConstructorDescriptor> getUsableConstructors() {
        return constructors.stream().filter(ConstructorDescriptor::isUsable).collect(Collectors.toList());
    }
}
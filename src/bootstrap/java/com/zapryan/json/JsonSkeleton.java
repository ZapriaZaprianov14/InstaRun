package com.zapryan.json;

import com.zapryan.reflection.PropertyDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates example JSON for a type, like the example value Swagger shows for a request body: every property that
 * can be entered, with a placeholder of its type, and one element in every list. Beans nest at most
 * {@value #MAX_BEAN_DEPTH} deep and a bean inside itself is cut, so self-referencing types stay finite; a cut bean
 * becomes null, or an empty array where it would have been the element.
 */
public final class JsonSkeleton {

    static final int MAX_BEAN_DEPTH = 3;

    private JsonSkeleton() {
    }

    public static JsonNode of(TypeDescriptor type) {
        JsonNode skeleton = of(type, Collections.<TypeDescriptor.BeanType>emptyList());
        return skeleton != null ? skeleton : new JsonNode.NullNode();
    }

    /** Returns null for a bean that is cut. */
    private static @Nullable JsonNode of(TypeDescriptor type, List<TypeDescriptor.BeanType> enclosingBeans) {
        if (!type.isSupported()) {
            return new JsonNode.NullNode();
        }
        if (type instanceof TypeDescriptor.LiteralType) {
            return placeholder((TypeDescriptor.LiteralType) type);
        }
        if (type instanceof TypeDescriptor.EnumType) {
            List<String> constants = ((TypeDescriptor.EnumType) type).getConstants();
            return constants.isEmpty() ? new JsonNode.NullNode() : new JsonNode.StringNode(constants.get(0));
        }
        if (type instanceof TypeDescriptor.SequenceType) {
            JsonNode element = of(((TypeDescriptor.SequenceType) type).getElementType(), enclosingBeans);
            return new JsonNode.ArrayNode(element != null
                    ? Collections.singletonList(element)
                    : Collections.<JsonNode>emptyList());
        }
        TypeDescriptor.BeanType bean = (TypeDescriptor.BeanType) type;
        if (enclosingBeans.contains(bean) || enclosingBeans.size() >= MAX_BEAN_DEPTH) {
            return null;
        }
        List<TypeDescriptor.BeanType> path = new ArrayList<>(enclosingBeans);
        path.add(bean);
        List<JsonNode.Member> members = new ArrayList<>();
        for (PropertyDescriptor property : bean.getProperties()) {
            if (property.getType().isSupported()) {
                JsonNode value = of(property.getType(), path);
                members.add(new JsonNode.Member(property.getName(), value != null ? value : new JsonNode.NullNode()));
            }
        }
        return new JsonNode.ObjectNode(members);
    }

    private static JsonNode placeholder(TypeDescriptor.LiteralType type) {
        switch (type.getKind()) {
            case TEXT:
                return new JsonNode.StringNode("string");
            case CHARACTER:
                return new JsonNode.StringNode("a");
            case BOOLEAN:
                return new JsonNode.BooleanNode(true);
            case INTEGER:
                return new JsonNode.NumberNode("0");
            default:
                return new JsonNode.NumberNode("0.0");
        }
    }
}
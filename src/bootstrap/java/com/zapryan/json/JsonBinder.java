package com.zapryan.json;

import com.zapryan.reflection.ConstructorDescriptor;
import com.zapryan.reflection.Literals;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.PropertyDescriptor;
import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.SetterDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns JSON into the {@link ValueSpec} of a declared type, strictly: the kind of JSON value must match the type,
 * numbers must fit it exactly and every property must exist. A bean is built by the constructor whose parameters are
 * all given and that takes the most of them; the properties it doesn't take are set through setters, in JSON order.
 */
public final class JsonBinder {

    private JsonBinder() {
    }

    public static ValueSpec bind(JsonNode json, TypeDescriptor type) throws JsonBindingException {
        return bind(json, type, "");
    }

    private static ValueSpec bind(JsonNode json, TypeDescriptor type, String path) throws JsonBindingException {
        if (json instanceof JsonNode.NullNode) {
            if (!type.isNullable()) {
                throw problem(JsonProblem.NULL_FOR_PRIMITIVE, path, json, type.getDisplayName());
            }
            return new ValueSpec.Null();
        }
        if (!type.isSupported()) {
            throw new JsonBindingException(JsonProblem.UNSUPPORTED_TYPE, path, json.getOffset(), type,
                    type.getDisplayName());
        }
        if (type instanceof TypeDescriptor.LiteralType) {
            return bindLiteral(json, (TypeDescriptor.LiteralType) type, path);
        }
        if (type instanceof TypeDescriptor.EnumType) {
            return bindEnum(json, (TypeDescriptor.EnumType) type, path);
        }
        if (type instanceof TypeDescriptor.SequenceType) {
            return bindSequence(json, (TypeDescriptor.SequenceType) type, path);
        }
        if (type instanceof TypeDescriptor.BeanType) {
            return bindBean(json, (TypeDescriptor.BeanType) type, path);
        }
        throw new IllegalArgumentException("Unknown type descriptor: " + type);
    }

    private static ValueSpec bindLiteral(JsonNode json, TypeDescriptor.LiteralType type, String path)
            throws JsonBindingException {
        switch (type.getKind()) {
            case TEXT:
            case CHARACTER:
                if (!(json instanceof JsonNode.StringNode)) {
                    throw problem(JsonProblem.EXPECTED_STRING, path, json, type.getDisplayName());
                }
                return literal(((JsonNode.StringNode) json).getValue(), type, path, json);
            case BOOLEAN:
                if (!(json instanceof JsonNode.BooleanNode)) {
                    throw problem(JsonProblem.EXPECTED_BOOLEAN, path, json, type.getDisplayName());
                }
                return new ValueSpec.Literal(((JsonNode.BooleanNode) json).getValue());
            default:
                if (!(json instanceof JsonNode.NumberNode)) {
                    throw problem(JsonProblem.EXPECTED_NUMBER, path, json, type.getDisplayName());
                }
                return literal(((JsonNode.NumberNode) json).getText(), type, path, json);
        }
    }

    private static ValueSpec literal(String text, TypeDescriptor.LiteralType type, String path, JsonNode json)
            throws JsonBindingException {
        try {
            return new ValueSpec.Literal(Literals.parse(type.getTypeName(), text));
        } catch (IllegalArgumentException e) {
            throw problem(JsonProblem.INVALID_LITERAL, path, json, text, type.getDisplayName());
        }
    }

    private static ValueSpec bindEnum(JsonNode json, TypeDescriptor.EnumType type, String path)
            throws JsonBindingException {
        if (!(json instanceof JsonNode.StringNode)) {
            throw problem(JsonProblem.EXPECTED_STRING, path, json, type.getDisplayName());
        }
        String name = ((JsonNode.StringNode) json).getValue();
        if (!type.getConstants().contains(name)) {
            throw problem(JsonProblem.UNKNOWN_ENUM_CONSTANT, path, json,
                    name, type.getDisplayName(), String.join(", ", type.getConstants()));
        }
        return new ValueSpec.EnumConstant(type.getBinaryName(), name);
    }

    private static ValueSpec bindSequence(JsonNode json, TypeDescriptor.SequenceType type, String path)
            throws JsonBindingException {
        if (!(json instanceof JsonNode.ArrayNode)) {
            throw problem(JsonProblem.EXPECTED_ARRAY, path, json, type.getDisplayName());
        }
        List<JsonNode> nodes = ((JsonNode.ArrayNode) json).getElements();
        List<ValueSpec> elements = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            elements.add(bind(nodes.get(i), type.getElementType(), path + "[" + i + "]"));
        }
        if (type instanceof TypeDescriptor.CollectionType) {
            String implementation = ((TypeDescriptor.CollectionType) type).getImplementationClassName();
            return new ValueSpec.CollectionOf(implementation, elements);
        }
        return new ValueSpec.ArrayOf(((TypeDescriptor.ArrayType) type).getComponentTypeName(), elements);
    }

    private static ValueSpec bindBean(JsonNode json, TypeDescriptor.BeanType type, String path)
            throws JsonBindingException {
        if (!(json instanceof JsonNode.ObjectNode)) {
            throw problem(JsonProblem.EXPECTED_OBJECT, path, json, type.getDisplayName());
        }
        Map<String, JsonNode.Member> members = membersByName((JsonNode.ObjectNode) json, path);
        List<String> propertyNames = new ArrayList<>();
        for (PropertyDescriptor property : type.getProperties()) {
            propertyNames.add(property.getName());
        }
        for (JsonNode.Member member : members.values()) {
            if (!propertyNames.contains(member.getName())) {
                throw new JsonBindingException(JsonProblem.UNKNOWN_PROPERTY, path, member.getOffset(),
                        member.getName(), type.getDisplayName(), String.join(", ", propertyNames));
            }
        }

        ConstructorDescriptor constructor = chooseConstructor(type, members, path, json);
        List<String> parameterTypes = new ArrayList<>();
        List<ValueSpec> arguments = new ArrayList<>();
        List<String> consumed = new ArrayList<>();
        for (ParameterDescriptor parameter : constructor.getParameters()) {
            JsonNode.Member member = members.get(parameter.getName());
            parameterTypes.add(parameter.getErasedTypeName());
            arguments.add(bind(member.getValue(), parameter.getType(), path + "." + member.getName()));
            consumed.add(parameter.getName());
        }

        List<SetterCall> setterCalls = new ArrayList<>();
        for (JsonNode.Member member : members.values()) {
            if (consumed.contains(member.getName())) {
                continue;
            }
            SetterDescriptor setter = type.findSetter(member.getName());
            if (setter == null) {
                throw new JsonBindingException(JsonProblem.NOT_SETTABLE, path, member.getOffset(),
                        member.getName(), type.getDisplayName());
            }
            ValueSpec value = bind(member.getValue(), setter.getType(), path + "." + member.getName());
            setterCalls.add(new SetterCall(setter.getDeclaringClassBinaryName(), setter.getMethodName(),
                    setter.getErasedParameterTypeName(), value));
        }
        return new ValueSpec.Constructed(type.getBinaryName(), parameterTypes, arguments, setterCalls);
    }

    private static Map<String, JsonNode.Member> membersByName(JsonNode.ObjectNode object, String path)
            throws JsonBindingException {
        Map<String, JsonNode.Member> members = new LinkedHashMap<>();
        for (JsonNode.Member member : object.getMembers()) {
            if (members.put(member.getName(), member) != null) {
                throw new JsonBindingException(JsonProblem.DUPLICATE_PROPERTY, path, member.getOffset(),
                        member.getName());
            }
        }
        return members;
    }

    /** The usable constructor whose parameters are all given and that takes the most of them. */
    private static ConstructorDescriptor chooseConstructor(TypeDescriptor.BeanType type,
                                                           Map<String, JsonNode.Member> members, String path,
                                                           JsonNode json) throws JsonBindingException {
        ConstructorDescriptor best = null;
        List<String> closestMissing = null;
        for (ConstructorDescriptor constructor : type.getUsableConstructors()) {
            List<String> missing = new ArrayList<>();
            for (ParameterDescriptor parameter : constructor.getParameters()) {
                if (!members.containsKey(parameter.getName())) {
                    missing.add(parameter.getName());
                }
            }
            if (missing.isEmpty()) {
                if (best == null || constructor.getParameters().size() > best.getParameters().size()) {
                    best = constructor;
                }
            } else if (closestMissing == null || missing.size() < closestMissing.size()) {
                closestMissing = missing;
            }
        }
        if (best == null) {
            throw problem(JsonProblem.NO_MATCHING_CONSTRUCTOR, path, json,
                    type.getDisplayName(), String.join(", ", closestMissing));
        }
        return best;
    }

    private static JsonBindingException problem(JsonProblem problem, String path, JsonNode json, String... arguments) {
        return new JsonBindingException(problem, path, json.getOffset(), arguments);
    }
}
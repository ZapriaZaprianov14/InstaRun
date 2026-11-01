package com.instarun.bootstrap;

import com.instarun.json.JsonNode;
import com.instarun.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes a recorded value as JSON for the user to read: an object by its properties, named after their getters,
 * arrays and collections as arrays, maps with literal keys as objects, and JDK objects by their text. What wasn't
 * recorded is shown as a short note in angle brackets, e.g. {@code "<cycle>"}, and a collection recorded only in
 * part ends with how many elements are left out, e.g. {@code "<5 more>"}.
 */
public final class ObservationJson {

    private ObservationJson() {
    }

    public static String write(Observation observation) {
        return JsonWriter.write(toJson(observation));
    }

    static JsonNode toJson(Observation observation) {
        if (observation instanceof Observation.Literal) {
            return literal(((Observation.Literal) observation).getValue());
        }
        if (observation instanceof Observation.EnumConstant) {
            return new JsonNode.StringNode(((Observation.EnumConstant) observation).getConstantName());
        }
        if (observation instanceof Observation.Text) {
            return new JsonNode.StringNode(((Observation.Text) observation).getText());
        }
        if (observation instanceof Observation.Sequence) {
            Observation.Sequence sequence = (Observation.Sequence) observation;
            List<JsonNode> elements = new ArrayList<>();
            for (Observation element : sequence.getElements()) {
                elements.add(toJson(element));
            }
            if (!sequence.isComplete()) {
                elements.add(new JsonNode.StringNode(more(sequence.getSize() - sequence.getElements().size())));
            }
            return new JsonNode.ArrayNode(elements);
        }
        if (observation instanceof Observation.MapOf) {
            return map((Observation.MapOf) observation);
        }
        if (observation instanceof Observation.Bean) {
            List<JsonNode.Member> members = new ArrayList<>();
            for (Map.Entry<String, Observation> property : ((Observation.Bean) observation).getProperties().entrySet()) {
                members.add(new JsonNode.Member(propertyName(property.getKey()), toJson(property.getValue())));
            }
            return new JsonNode.ObjectNode(members);
        }
        if (observation instanceof Observation.Unrecorded) {
            return new JsonNode.StringNode(note((Observation.Unrecorded) observation));
        }
        return new JsonNode.NullNode();
    }

    /** The name of the property an accessor reads, e.g. {@code name} for getName and isName, {@code URL} for getURL. */
    static String propertyName(String accessor) {
        String name = accessor.startsWith("get") && accessor.length() > 3 && Character.isUpperCase(accessor.charAt(3))
                ? accessor.substring(3)
                : accessor.startsWith("is") && accessor.length() > 2 && Character.isUpperCase(accessor.charAt(2))
                ? accessor.substring(2)
                : accessor;   // a record's accessor is named after its component
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static JsonNode literal(Object value) {
        if (value instanceof Boolean) {
            return new JsonNode.BooleanNode((Boolean) value);
        }
        boolean finite = !(value instanceof Double && (((Double) value).isNaN() || ((Double) value).isInfinite()))
                && !(value instanceof Float && (((Float) value).isNaN() || ((Float) value).isInfinite()));
        if (value instanceof Number && finite) {
            return new JsonNode.NumberNode(value.toString());
        }
        // Strings, characters, and NaN and the infinities, which JSON has no numbers for.
        return new JsonNode.StringNode(value.toString());
    }

    /** A map whose keys are all literals, enum constants or text is an object; any other is a list of entries. */
    private static JsonNode map(Observation.MapOf map) {
        boolean textKeys = true;
        for (Observation.Entry entry : map.getEntries()) {
            Observation key = entry.getKey();
            textKeys &= key instanceof Observation.Literal || key instanceof Observation.EnumConstant
                    || key instanceof Observation.Text;
        }
        int left = map.getSize() - map.getEntries().size();
        if (textKeys) {
            List<JsonNode.Member> members = new ArrayList<>();
            for (Observation.Entry entry : map.getEntries()) {
                members.add(new JsonNode.Member(keyText(entry.getKey()), toJson(entry.getValue())));
            }
            if (left > 0) {
                members.add(new JsonNode.Member("...", new JsonNode.StringNode(more(left))));
            }
            return new JsonNode.ObjectNode(members);
        }
        List<JsonNode> entries = new ArrayList<>();
        for (Observation.Entry entry : map.getEntries()) {
            List<JsonNode.Member> members = new ArrayList<>();
            members.add(new JsonNode.Member("key", toJson(entry.getKey())));
            members.add(new JsonNode.Member("value", toJson(entry.getValue())));
            entries.add(new JsonNode.ObjectNode(members));
        }
        if (left > 0) {
            entries.add(new JsonNode.StringNode(more(left)));
        }
        return new JsonNode.ArrayNode(entries);
    }

    private static String keyText(Observation key) {
        if (key instanceof Observation.Literal) {
            return String.valueOf(((Observation.Literal) key).getValue());
        }
        if (key instanceof Observation.EnumConstant) {
            return ((Observation.EnumConstant) key).getConstantName();
        }
        return ((Observation.Text) key).getText();
    }

    private static String note(Observation.Unrecorded unrecorded) {
        switch (unrecorded.getReason()) {
            case CYCLE:
                return "<cycle>";
            case TOO_DEEP:
                return "<too deep>";
            case LIMIT_REACHED:
                return "<too many values>";
            case THREW:
                return "<threw " + unrecorded.getDetail() + ">";
            default:
                return "<unreadable>";
        }
    }

    private static String more(int count) {
        return "<" + count + " more>";
    }
}
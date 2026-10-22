package com.zapryan.json;

import java.util.Arrays;

/** Builds JSON values by hand, the way the plugin builds them from JSON PSI or from the form. */
final class TestJson {

    private TestJson() {
    }

    static JsonNode.ObjectNode object(JsonNode.Member... members) {
        return new JsonNode.ObjectNode(Arrays.asList(members));
    }

    static JsonNode.Member member(String name, JsonNode value) {
        return new JsonNode.Member(name, value);
    }

    static JsonNode.ArrayNode array(JsonNode... elements) {
        return new JsonNode.ArrayNode(Arrays.asList(elements));
    }

    static JsonNode.StringNode string(String value) {
        return new JsonNode.StringNode(value);
    }

    static JsonNode.NumberNode number(String text) {
        return new JsonNode.NumberNode(text);
    }

    static JsonNode.BooleanNode bool(boolean value) {
        return new JsonNode.BooleanNode(value);
    }

    static JsonNode.NullNode nothing() {
        return new JsonNode.NullNode();
    }

    /** Joins lines with \n, for comparing against {@link JsonWriter} output. */
    static String lines(String... lines) {
        return String.join("\n", lines);
    }
}
package com.zapryan.json;

import java.util.List;

/** Writes a {@link JsonNode} as indented JSON text. */
public final class JsonWriter {

    private static final String INDENT = "  ";

    private JsonWriter() {
    }

    public static String write(JsonNode node) {
        StringBuilder out = new StringBuilder();
        write(node, 0, out);
        return out.toString();
    }

    private static void write(JsonNode node, int depth, StringBuilder out) {
        if (node instanceof JsonNode.ObjectNode) {
            writeObject(((JsonNode.ObjectNode) node).getMembers(), depth, out);
        } else if (node instanceof JsonNode.ArrayNode) {
            writeArray(((JsonNode.ArrayNode) node).getElements(), depth, out);
        } else if (node instanceof JsonNode.StringNode) {
            quote(((JsonNode.StringNode) node).getValue(), out);
        } else if (node instanceof JsonNode.NumberNode) {
            out.append(((JsonNode.NumberNode) node).getText());
        } else if (node instanceof JsonNode.BooleanNode) {
            out.append(((JsonNode.BooleanNode) node).getValue());
        } else {
            out.append("null");
        }
    }

    private static void writeObject(List<JsonNode.Member> members, int depth, StringBuilder out) {
        if (members.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        for (int i = 0; i < members.size(); i++) {
            indent(depth + 1, out);
            quote(members.get(i).getName(), out);
            out.append(": ");
            write(members.get(i).getValue(), depth + 1, out);
            out.append(i < members.size() - 1 ? ",\n" : "\n");
        }
        indent(depth, out);
        out.append('}');
    }

    private static void writeArray(List<JsonNode> elements, int depth, StringBuilder out) {
        if (elements.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < elements.size(); i++) {
            indent(depth + 1, out);
            write(elements.get(i), depth + 1, out);
            out.append(i < elements.size() - 1 ? ",\n" : "\n");
        }
        indent(depth, out);
        out.append(']');
    }

    private static void indent(int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
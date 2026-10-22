package com.zapryan.json;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A JSON value, independent of whatever parsed or built it. Numbers keep their exact source text, so they are
 * converted straight to the declared type and never pass through double.
 */
@Getter
public abstract class JsonNode {

    /** Where the value starts in the text it was parsed from; -1 when it was built in code. */
    private final int offset;

    private JsonNode(int offset) {
        this.offset = offset;
    }

    @Getter
    public static final class ObjectNode extends JsonNode {
        private final List<Member> members;

        public ObjectNode(List<Member> members) {
            this(members, -1);
        }

        public ObjectNode(List<Member> members, int offset) {
            super(offset);
            this.members = new ArrayList<>(members);
        }

        public @Nullable JsonNode get(String name) {
            for (Member member : members) {
                if (member.getName().equals(name)) {
                    return member.getValue();
                }
            }
            return null;
        }
    }

    /** One {@code "name": value} pair of an object. */
    @Getter
    public static final class Member {
        private final String name;
        private final JsonNode value;
        /** Where the name starts; -1 when built in code. */
        private final int offset;

        public Member(String name, JsonNode value) {
            this(name, value, -1);
        }

        public Member(String name, JsonNode value, int offset) {
            this.name = name;
            this.value = value;
            this.offset = offset;
        }
    }

    @Getter
    public static final class ArrayNode extends JsonNode {
        private final List<JsonNode> elements;

        public ArrayNode(List<JsonNode> elements) {
            this(elements, -1);
        }

        public ArrayNode(List<JsonNode> elements, int offset) {
            super(offset);
            this.elements = new ArrayList<>(elements);
        }
    }

    @Getter
    public static final class StringNode extends JsonNode {
        private final String value;

        public StringNode(String value) {
            this(value, -1);
        }

        public StringNode(String value, int offset) {
            super(offset);
            this.value = value;
        }
    }

    @Getter
    public static final class NumberNode extends JsonNode {
        /** The number exactly as written, e.g. {@code 1.50} or {@code 12e3}. */
        private final String text;

        public NumberNode(String text) {
            this(text, -1);
        }

        public NumberNode(String text, int offset) {
            super(offset);
            this.text = text;
        }
    }

    public static final class BooleanNode extends JsonNode {
        private final boolean value;

        public BooleanNode(boolean value) {
            this(value, -1);
        }

        public BooleanNode(boolean value, int offset) {
            super(offset);
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }
    }

    public static final class NullNode extends JsonNode {

        public NullNode() {
            this(-1);
        }

        public NullNode(int offset) {
            super(offset);
        }
    }
}
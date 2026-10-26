package com.zapryan.codegen;

import java.util.HashSet;
import java.util.Set;

/** Picks the local variable names of one generated test: valid, readable and unique. */
final class VariableNames {

    private static final Set<String> RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var",
            "yield", "record", "sealed", "permits", "_");

    private final Set<String> taken = new HashSet<>();

    /** A name not taken yet, based on the hint: {@code person}, then {@code person2}, {@code person3} and so on. */
    String allocate(String hint) {
        String base = sanitize(hint);
        String name = base;
        for (int i = 2; taken.contains(name); i++) {
            name = base + i;
        }
        taken.add(name);
        return name;
    }

    /**
     * A variable name for a class's simple name: {@code orderService} for {@code OrderService} and {@code urlParser}
     * for {@code URLParser}.
     */
    static String forClass(String simpleName) {
        int upper = 0;
        while (upper < simpleName.length() && Character.isUpperCase(simpleName.charAt(upper))) {
            upper++;
        }
        if (upper == 0) {
            return simpleName;
        }
        // In "URLParser" the P starts the next word, so only "URL" is lowered.
        int lowered = upper > 1 && upper < simpleName.length() ? upper - 1 : upper;
        return simpleName.substring(0, lowered).toLowerCase() + simpleName.substring(lowered);
    }

    private static String sanitize(String hint) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < hint.length(); i++) {
            char c = hint.charAt(i);
            if (name.length() == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) {
                name.append(c);
            }
        }
        if (name.length() == 0) {
            return "value";
        }
        String result = name.toString();
        return RESERVED.contains(result) ? result + "Value" : result;
    }
}
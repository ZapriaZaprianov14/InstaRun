package com.zapryan.json;

import com.zapryan.reflection.TypeDescriptor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** JSON text that doesn't give a value of its declared type, with where and why. */
@Getter
public final class JsonBindingException extends Exception {

    private static final long serialVersionUID = 1L;

    private final JsonProblem problem;
    /** Where in the value the problem is, e.g. {@code .items[2].price}; empty for the value itself. */
    private final String path;
    /** Where in the text to point at; -1 when the JSON wasn't parsed from text. */
    private final int offset;
    private final List<String> arguments;
    /** The declared type at the path; set for {@link JsonProblem#UNSUPPORTED_TYPE}, whose message explains it. */
    private final transient @Nullable TypeDescriptor type;

    public JsonBindingException(JsonProblem problem, String path, int offset, String... arguments) {
        this(problem, path, offset, null, arguments);
    }

    public JsonBindingException(JsonProblem problem, String path, int offset, @Nullable TypeDescriptor type,
                                String... arguments) {
        super(problem + " at '" + path + "' " + Arrays.toString(arguments));
        this.problem = problem;
        this.path = path;
        this.offset = offset;
        this.type = type;
        this.arguments = Arrays.asList(arguments);
    }
}
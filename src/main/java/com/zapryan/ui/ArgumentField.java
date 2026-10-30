package com.zapryan.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ValidationInfo;
import com.zapryan.json.JsonBinder;
import com.zapryan.json.JsonBindingException;
import com.zapryan.json.JsonNode;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import com.zapryan.ui.form.FormEditor;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * One input row of the dialog for a parameter whose value fits on a line: a literal typed in, e.g. an int or a String,
 * or an enum constant picked from a list. The input is the form view's own editor for the type; this adds the label
 * and remembers the last value entered under the storage key.
 */
final class ArgumentField implements ValueEditor {

    private static final String NULL_SUFFIX = ".null";

    private final ParameterDescriptor parameter;
    private final TypeDescriptor type;
    private final PropertiesComponent storage;
    private final String storageKey;
    private final FormEditor input;

    /** @param parameter a parameter of a {@link TypeDescriptor.LiteralType} or {@link TypeDescriptor.EnumType} */
    ArgumentField(ParameterDescriptor parameter, PropertiesComponent storage, String storageKey) {
        this.parameter = parameter;
        this.type = parameter.getType();
        this.storage = storage;
        this.storageKey = storageKey;
        this.input = FormEditor.create(type, remembered());
    }

    @Override
    public String label() {
        return MessageBundler.message("dialog.argument.label", parameter.getName(), type.getDisplayName());
    }

    @Override
    public JComponent component() {
        return input.component();
    }

    @Override
    public @Nullable ValidationInfo validate() {
        return input.validate();
    }

    @Override
    public ValueSpec toValueSpec() {
        try {
            return JsonBinder.bind(input.toJson(), type);
        } catch (JsonBindingException e) {
            throw new IllegalStateException("A validated value didn't bind: " + e.getMessage(), e);
        }
    }

    @Override
    public void save() {
        JsonNode value = input.toJson();
        boolean isNull = value instanceof JsonNode.NullNode;
        if (!isNull) {
            storage.setValue(storageKey, textOf(value));
        }
        storage.setValue(storageKey + NULL_SUFFIX, isNull);
    }

    /** The value entered last time, as the JSON the editor starts from. */
    private JsonNode remembered() {
        if (type.isNullable() && storage.getBoolean(storageKey + NULL_SUFFIX)) {
            return new JsonNode.NullNode();
        }
        String text = storage.getValue(storageKey, "");
        if (!(type instanceof TypeDescriptor.LiteralType)) {
            // An enum constant's name; one the enum no longer has leaves its first constant chosen.
            return new JsonNode.StringNode(text);
        }
        return switch (((TypeDescriptor.LiteralType) type).getKind()) {
            case TEXT, CHARACTER -> new JsonNode.StringNode(text);
            case BOOLEAN -> new JsonNode.BooleanNode(!text.trim().equalsIgnoreCase("false"));
            default -> new JsonNode.NumberNode(text);
        };
    }

    private static String textOf(JsonNode value) {
        if (value instanceof JsonNode.StringNode) {
            return ((JsonNode.StringNode) value).getValue();
        }
        if (value instanceof JsonNode.BooleanNode) {
            return String.valueOf(((JsonNode.BooleanNode) value).getValue());
        }
        return ((JsonNode.NumberNode) value).getText();
    }
}
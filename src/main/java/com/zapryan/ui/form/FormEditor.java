package com.zapryan.ui.form;

import com.intellij.openapi.ui.ValidationInfo;
import com.zapryan.json.JsonNode;
import com.zapryan.reflection.TypeDescriptor;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * An editor for one value in the form view of a parameter. The form never builds values itself: it starts from JSON
 * and gives JSON back, which then goes through the same binding as JSON typed by hand.
 */
public abstract class FormEditor {

    /**
     * Builds the editor for a type, filled from the JSON. JSON that doesn't fit the type leaves the editor empty,
     * or null where the type allows it.
     */
    public static FormEditor create(TypeDescriptor type, JsonNode initial) {
        if (type instanceof TypeDescriptor.LiteralType) {
            return new LiteralFormEditor((TypeDescriptor.LiteralType) type, initial);
        }
        if (type instanceof TypeDescriptor.EnumType) {
            return new EnumFormEditor((TypeDescriptor.EnumType) type, initial);
        }
        if (type instanceof TypeDescriptor.SequenceType) {
            return new SequenceFormEditor((TypeDescriptor.SequenceType) type, initial);
        }
        if (type instanceof TypeDescriptor.BeanType) {
            return new BeanFormEditor((TypeDescriptor.BeanType) type, initial);
        }
        throw new IllegalArgumentException("No form editor for " + type.getDisplayName());
    }

    public abstract JComponent component();

    /** Checks what was typed and brings a problem hidden in another list element into view; null if all is valid. */
    public abstract @Nullable ValidationInfo validate();

    /** Only valid after {@link #validate()} returned null. */
    public abstract JsonNode toJson();

    /** Whether the editor is a nested structure whose label belongs above it. */
    boolean isTall() {
        return false;
    }
}
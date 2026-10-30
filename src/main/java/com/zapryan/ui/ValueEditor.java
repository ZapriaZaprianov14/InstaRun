package com.zapryan.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Consumer;

/**
 * The input for one parameter in {@link MethodInputDialog}. It remembers its last value under a storage key.
 */
interface ValueEditor {

    /**
     * A text field for a literal, a dropdown for an enum, and a JSON editor with a form view for anything else.
     *
     * @param errorReporter shows a problem found outside of validation, such as JSON that can't be switched to
     *                      the form; null clears it
     */
    static ValueEditor forParameter(ParameterDescriptor parameter, Project project, PropertiesComponent storage,
                                    String storageKey, Consumer<@Nullable ValidationInfo> errorReporter) {
        TypeDescriptor type = parameter.getType();
        if (type instanceof TypeDescriptor.LiteralType || type instanceof TypeDescriptor.EnumType) {
            return new ArgumentField(parameter, storage, storageKey);
        }
        return new StructuredField(parameter, project, storage, storageKey, errorReporter);
    }

    String label();

    JComponent component();

    /** A tall editor gets its label above it rather than beside it. */
    default boolean isTall() {
        return false;
    }

    @Nullable ValidationInfo validate();

    /** Only valid after {@link #validate()} returned null. */
    ValueSpec toValueSpec();

    void save();
}
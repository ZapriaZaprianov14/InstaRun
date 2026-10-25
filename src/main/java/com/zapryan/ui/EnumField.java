package com.zapryan.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** A dropdown of an enum parameter's constants, plus a "null" checkbox. Remembers the last choice. */
final class EnumField implements ValueEditor {

    private static final String NULL_SUFFIX = ".null";

    private final ParameterDescriptor parameter;
    private final TypeDescriptor.EnumType type;
    private final PropertiesComponent storage;
    private final String storageKey;
    private final ComboBox<String> constants;
    private final JBCheckBox nullBox;
    private final JPanel component = new JPanel(new BorderLayout(JBUI.scale(8), 0));

    EnumField(ParameterDescriptor parameter, TypeDescriptor.EnumType type, PropertiesComponent storage,
              String storageKey) {
        this.parameter = parameter;
        this.type = type;
        this.storage = storage;
        this.storageKey = storageKey;
        constants = new ComboBox<>(type.getConstants().toArray(new String[0]));
        String remembered = storage.getValue(storageKey);
        if (remembered != null && type.getConstants().contains(remembered)) {
            constants.setSelectedItem(remembered);
        }
        nullBox = new JBCheckBox(MessageBundler.message("dialog.null"), storage.getBoolean(storageKey + NULL_SUFFIX));
        nullBox.addItemListener(event -> constants.setEnabled(!nullBox.isSelected()));
        constants.setEnabled(!nullBox.isSelected());
        component.add(constants, BorderLayout.CENTER);
        component.add(nullBox, BorderLayout.EAST);
    }

    @Override
    public String label() {
        return MessageBundler.message("dialog.argument.label", parameter.getName(), type.getDisplayName());
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public @Nullable ValidationInfo validate() {
        if (!nullBox.isSelected() && constants.getSelectedIndex() < 0) {
            return new ValidationInfo(MessageBundler.message("dialog.enum.empty", type.getDisplayName()), constants);
        }
        return null;
    }

    @Override
    public ValueSpec toValueSpec() {
        return nullBox.isSelected()
                ? new ValueSpec.Null()
                : new ValueSpec.EnumConstant(type.getBinaryName(), constants.getItemAt(constants.getSelectedIndex()));
    }

    @Override
    public void save() {
        if (constants.getSelectedIndex() >= 0) {
            storage.setValue(storageKey, constants.getItemAt(constants.getSelectedIndex()));
        }
        storage.setValue(storageKey + NULL_SUFFIX, nullBox.isSelected());
    }
}
package com.zapryan.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.zapryan.reflection.Literals;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * One input row of the dialog: a text field for one parameter, plus a "null" checkbox when the type allows null.
 * The last value entered is remembered under the given storage key.
 */
final class ArgumentField implements ValueEditor {

    private static final String NULL_SUFFIX = ".null";

    private final ParameterDescriptor parameter;
    private final TypeDescriptor.LiteralType type;
    private final PropertiesComponent storage;
    private final String storageKey;
    private final JBTextField text = new JBTextField(24);
    private final @Nullable JBCheckBox nullBox;
    private final JPanel component = new JPanel(new BorderLayout(JBUI.scale(8), 0));

    ArgumentField(ParameterDescriptor parameter, TypeDescriptor.LiteralType type, PropertiesComponent storage,
                  String storageKey) {
        this.parameter = parameter;
        this.type = type;
        this.storage = storage;
        this.storageKey = storageKey;
        text.setText(storage.getValue(storageKey, ""));
        component.add(text, BorderLayout.CENTER);
        if (type.isNullable()) {
            JBCheckBox box = new JBCheckBox(MessageBundler.message("dialog.null"), storage.getBoolean(storageKey + NULL_SUFFIX));
            box.addItemListener(event -> text.setEnabled(!box.isSelected()));
            text.setEnabled(!box.isSelected());
            component.add(box, BorderLayout.EAST);
            nullBox = box;
        } else {
            nullBox = null;
        }
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
        if (isNull()) {
            return null;
        }
        try {
            Literals.parse(type.getTypeName(), text.getText());
            return null;
        } catch (IllegalArgumentException e) {
            String typed = StringUtil.escapeXmlEntities(text.getText());
            return new ValidationInfo(MessageBundler.message("dialog.invalid", typed, type.getDisplayName()), text);
        }
    }

    @Override
    public ValueSpec toValueSpec() {
        return isNull()
                ? new ValueSpec.Null()
                : new ValueSpec.Literal(Literals.parse(type.getTypeName(), text.getText()));
    }

    @Override
    public void save() {
        storage.setValue(storageKey, text.getText());
        storage.setValue(storageKey + NULL_SUFFIX, isNull());
    }

    private boolean isNull() {
        return nullBox != null && nullBox.isSelected();
    }
}
package com.zapryan.ui.form;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.zapryan.json.JsonNode;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** A dropdown of an enum's constants plus a "null" checkbox. */
final class EnumFormEditor extends FormEditor {

    private final TypeDescriptor.EnumType type;
    private final ComboBox<String> constants;
    private final JBCheckBox nullBox;
    private final JPanel component = new JPanel(new BorderLayout(JBUI.scale(8), 0));

    EnumFormEditor(TypeDescriptor.EnumType type, JsonNode initial) {
        this.type = type;
        constants = new ComboBox<>(type.getConstants().toArray(new String[0]));
        String initialName = initial instanceof JsonNode.StringNode ? ((JsonNode.StringNode) initial).getValue() : null;
        if (type.getConstants().contains(initialName)) {
            constants.setSelectedItem(initialName);
        }
        nullBox = new JBCheckBox(MessageBundler.message("dialog.null"), initial instanceof JsonNode.NullNode);
        nullBox.addItemListener(event -> constants.setEnabled(!nullBox.isSelected()));
        constants.setEnabled(!nullBox.isSelected());
        component.add(constants, BorderLayout.CENTER);
        component.add(nullBox, BorderLayout.EAST);
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
    public JsonNode toJson() {
        return nullBox.isSelected()
                ? new JsonNode.NullNode()
                : new JsonNode.StringNode(constants.getItemAt(constants.getSelectedIndex()));
    }
}
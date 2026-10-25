package com.zapryan.ui.form;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.zapryan.json.JsonNode;
import com.zapryan.reflection.Literals;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** A text field, or a true/false dropdown for booleans, plus a "null" checkbox when the type allows null. */
final class LiteralFormEditor extends FormEditor {

    private final TypeDescriptor.LiteralType type;
    private final @Nullable JBTextField text;
    private final @Nullable ComboBox<String> flag;
    private final @Nullable JBCheckBox nullBox;
    private final JPanel component = new JPanel(new BorderLayout(JBUI.scale(8), 0));

    LiteralFormEditor(TypeDescriptor.LiteralType type, JsonNode initial) {
        this.type = type;
        JComponent input;
        if (type.getKind() == Literals.Kind.BOOLEAN) {
            flag = new ComboBox<>(new String[]{"true", "false"});
            boolean isFalse = initial instanceof JsonNode.BooleanNode && !((JsonNode.BooleanNode) initial).getValue();
            flag.setSelectedItem(isFalse ? "false" : "true");
            text = null;
            input = flag;
        } else {
            text = new JBTextField(textOf(initial), 20);
            flag = null;
            input = text;
        }
        component.add(input, BorderLayout.CENTER);
        if (type.isNullable()) {
            boolean isNull = initial instanceof JsonNode.NullNode;
            JBCheckBox box = new JBCheckBox(MessageBundler.message("dialog.null"), isNull);
            box.addItemListener(event -> input.setEnabled(!box.isSelected()));
            input.setEnabled(!box.isSelected());
            component.add(box, BorderLayout.EAST);
            nullBox = box;
        } else {
            nullBox = null;
        }
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public @Nullable ValidationInfo validate() {
        if (isNull() || text == null) {
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
    public JsonNode toJson() {
        if (isNull()) {
            return new JsonNode.NullNode();
        }
        if (flag != null) {
            return new JsonNode.BooleanNode("true".equals(flag.getItemAt(flag.getSelectedIndex())));
        }
        String value = text.getText();
        switch (type.getKind()) {
            case TEXT:
            case CHARACTER:
                return new JsonNode.StringNode(value);
            default:
                // Written back from the parsed number, so "+5" or " 5" become valid JSON.
                return new JsonNode.NumberNode(Literals.parse(type.getTypeName(), value).toString());
        }
    }

    private boolean isNull() {
        return nullBox != null && nullBox.isSelected();
    }

    private static String textOf(JsonNode node) {
        if (node instanceof JsonNode.StringNode) {
            return ((JsonNode.StringNode) node).getValue();
        }
        if (node instanceof JsonNode.NumberNode) {
            return ((JsonNode.NumberNode) node).getText();
        }
        return "";
    }
}
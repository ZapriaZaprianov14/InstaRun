package com.zapryan.ui.form;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.zapryan.json.JsonNode;
import com.zapryan.json.JsonSkeleton;
import com.zapryan.reflection.PropertyDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.ui.InputMessages;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A row per property of a bean, under a "null" checkbox. The rows are only built once the bean is not null, so a
 * bean that contains itself only nests as deep as the user opens it.
 */
final class BeanFormEditor extends FormEditor {

    private final TypeDescriptor.BeanType type;
    private final JBCheckBox nullBox;
    private final JPanel properties = new JPanel(new BorderLayout());
    private final Map<String, FormEditor> editors = new LinkedHashMap<>();
    private final JPanel component = new JPanel(new BorderLayout());
    private boolean built;

    BeanFormEditor(TypeDescriptor.BeanType type, JsonNode initial) {
        this.type = type;
        boolean isNull = !(initial instanceof JsonNode.ObjectNode);
        nullBox = new JBCheckBox(MessageBundler.message("dialog.null"), isNull);
        nullBox.addItemListener(event -> showProperties());
        properties.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLineLeft(JBColor.border()), JBUI.Borders.emptyLeft(8)));
        component.add(nullBox, BorderLayout.NORTH);
        component.add(properties, BorderLayout.CENTER);
        if (!isNull) {
            build((JsonNode.ObjectNode) initial);
        }
        properties.setVisible(!isNull);
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    boolean isTall() {
        return true;
    }

    @Override
    public @Nullable ValidationInfo validate() {
        if (nullBox.isSelected()) {
            return null;
        }
        for (FormEditor editor : editors.values()) {
            ValidationInfo problem = editor.validate();
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    @Override
    public JsonNode toJson() {
        if (nullBox.isSelected()) {
            return new JsonNode.NullNode();
        }
        List<JsonNode.Member> members = new ArrayList<>();
        for (Map.Entry<String, FormEditor> editor : editors.entrySet()) {
            members.add(new JsonNode.Member(editor.getKey(), editor.getValue().toJson()));
        }
        return new JsonNode.ObjectNode(members);
    }

    private void showProperties() {
        if (!nullBox.isSelected() && !built) {
            JsonNode skeleton = JsonSkeleton.of(type);
            build(skeleton instanceof JsonNode.ObjectNode
                    ? (JsonNode.ObjectNode) skeleton
                    : new JsonNode.ObjectNode(new ArrayList<>()));
        }
        properties.setVisible(!nullBox.isSelected());
        component.revalidate();
        component.repaint();
    }

    /** Properties the JSON doesn't give start from their skeleton value, since the form always sends every property. */
    private void build(JsonNode.ObjectNode json) {
        FormBuilder rows = FormBuilder.createFormBuilder();
        for (PropertyDescriptor property : type.getProperties()) {
            String label = MessageBundler.message("dialog.argument.label", property.getName(),
                    property.getType().getDisplayName());
            if (!property.getType().isSupported()) {
                rows.addLabeledComponent(label, ComponentPanelBuilder.createCommentComponent(
                        StringUtil.escapeXmlEntities(InputMessages.whyUnsupported(property.getType())), true));
                continue;
            }
            JsonNode value = json.get(property.getName());
            FormEditor editor = create(property.getType(), value != null ? value : JsonSkeleton.of(property.getType()));
            editors.put(property.getName(), editor);
            rows.addLabeledComponent(label, editor.component(), editor.isTall());
        }
        properties.add(rows.getPanel(), BorderLayout.CENTER);
        built = true;
    }
}
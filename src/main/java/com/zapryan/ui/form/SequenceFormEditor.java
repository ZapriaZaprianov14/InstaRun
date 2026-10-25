package com.zapryan.ui.form;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;
import com.zapryan.json.JsonNode;
import com.zapryan.json.JsonSkeleton;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * A list or array shown one element at a time: a numbered button per element to move between them, + to add an
 * element built from the element type's skeleton, and − to remove the element on show.
 */
final class SequenceFormEditor extends FormEditor {

    private final TypeDescriptor.SequenceType type;
    private final List<FormEditor> elements = new ArrayList<>();
    private final JBCheckBox nullBox;
    private final JPanel numbers = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(2), JBUI.scale(2)));
    private final JButton add = new JButton(AllIcons.General.Add);
    private final JButton remove = new JButton(AllIcons.General.Remove);
    private final JBLabel position = new JBLabel();
    private final JPanel shown = new JPanel(new BorderLayout());
    private final JPanel body = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    private final JPanel component = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    private int selected;

    SequenceFormEditor(TypeDescriptor.SequenceType type, JsonNode initial) {
        this.type = type;
        if (initial instanceof JsonNode.ArrayNode) {
            for (JsonNode element : ((JsonNode.ArrayNode) initial).getElements()) {
                elements.add(create(type.getElementType(), element));
            }
        }
        selected = elements.isEmpty() ? -1 : 0;

        add.setToolTipText(MessageBundler.message("dialog.list.add"));
        add.addActionListener(event -> {
            elements.add(create(type.getElementType(), JsonSkeleton.of(type.getElementType())));
            select(elements.size() - 1);
        });
        remove.setToolTipText(MessageBundler.message("dialog.list.remove"));
        remove.addActionListener(event -> {
            elements.remove(selected);
            select(Math.min(selected, elements.size() - 1));
        });
        nullBox = new JBCheckBox(MessageBundler.message("dialog.null"), initial instanceof JsonNode.NullNode);
        nullBox.addItemListener(event -> refresh());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
        actions.add(add);
        actions.add(remove);
        actions.add(nullBox);
        JPanel header = new JPanel(new BorderLayout());
        header.add(numbers, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);

        body.setBorder(JBUI.Borders.emptyLeft(8));
        body.add(position, BorderLayout.NORTH);
        body.add(shown, BorderLayout.CENTER);
        component.add(header, BorderLayout.NORTH);
        component.add(body, BorderLayout.CENTER);
        refresh();
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
        for (int i = 0; i < elements.size(); i++) {
            ValidationInfo problem = elements.get(i).validate();
            if (problem != null) {
                select(i);
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
        List<JsonNode> values = new ArrayList<>(elements.size());
        for (FormEditor element : elements) {
            values.add(element.toJson());
        }
        return new JsonNode.ArrayNode(values);
    }

    private void select(int index) {
        selected = index;
        refresh();
    }

    private void refresh() {
        boolean isNull = nullBox.isSelected();
        numbers.removeAll();
        for (int i = 0; i < elements.size(); i++) {
            int index = i;
            JToggleButton number = new JToggleButton(String.valueOf(i + 1), i == selected);
            number.addActionListener(event -> select(index));
            numbers.add(number);
        }
        shown.removeAll();
        if (selected >= 0) {
            shown.add(elements.get(selected).component(), BorderLayout.CENTER);
            position.setText(MessageBundler.message("dialog.list.position", selected + 1, elements.size()));
        } else {
            position.setText(MessageBundler.message("dialog.list.empty"));
        }
        numbers.setVisible(!isNull);
        body.setVisible(!isNull);
        add.setEnabled(!isNull);
        remove.setEnabled(!isNull && selected >= 0);
        component.revalidate();
        component.repaint();
    }
}
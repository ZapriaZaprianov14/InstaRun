package com.zapryan.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.zapryan.json.JsonBinder;
import com.zapryan.json.JsonBindingException;
import com.zapryan.json.JsonNode;
import com.zapryan.json.JsonPsiReader;
import com.zapryan.json.JsonSkeleton;
import com.zapryan.json.JsonWriter;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.PropertyDescriptor;
import com.zapryan.reflection.TypeDescriptor;
import com.zapryan.reflection.ValueSpec;
import com.zapryan.ui.form.FormEditor;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The input for a bean, list or array parameter: a JSON editor pre-filled with a skeleton of the type, and a form
 * view over the same JSON. Whichever is shown, the value is bound from JSON, so both are validated the same way.
 * Remembers the JSON and the chosen view.
 */
final class StructuredField implements ValueEditor {

    private enum View { JSON, FORM }

    private static final String JSON_SUFFIX = ".json";
    private static final String VIEW_SUFFIX = ".view";

    private final ParameterDescriptor parameter;
    private final TypeDescriptor type;
    private final Project project;
    private final PropertiesComponent storage;
    private final String storageKey;
    private final Consumer<@Nullable ValidationInfo> errorReporter;

    private final LanguageTextField jsonEditor;
    private final JPanel formHolder = new JPanel(new BorderLayout());
    private final JBScrollPane formScroll = new JBScrollPane(formHolder);
    private final CardLayout cards = new CardLayout();
    private final JPanel views = new JPanel(cards);
    private final JBRadioButton jsonButton = new JBRadioButton(MessageBundler.message("dialog.view.json"));
    private final JBRadioButton formButton = new JBRadioButton(MessageBundler.message("dialog.view.form"));
    private final JPanel component = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    private @Nullable FormEditor form;
    private View view = View.JSON;
    private @Nullable ValueSpec bound;

    StructuredField(ParameterDescriptor parameter, Project project, PropertiesComponent storage, String storageKey,
                    Consumer<@Nullable ValidationInfo> errorReporter) {
        this.parameter = parameter;
        this.type = parameter.getType();
        this.project = project;
        this.storage = storage;
        this.storageKey = storageKey;
        this.errorReporter = errorReporter;

        String json = storage.getValue(storageKey + JSON_SUFFIX);
        jsonEditor = new LanguageTextField(JsonLanguage.INSTANCE, project, json != null ? json : skeleton(), false);
        jsonEditor.addSettingsProvider(editor -> {
            editor.setVerticalScrollbarVisible(true);
            editor.setHorizontalScrollbarVisible(true);
            editor.getSettings().setLineNumbersShown(true);
        });
        Dimension size = new Dimension(JBUI.scale(560), JBUI.scale(260));
        jsonEditor.setPreferredSize(size);
        formScroll.setPreferredSize(size);
        views.add(jsonEditor, View.JSON.name());
        views.add(formScroll, View.FORM.name());

        ButtonGroup group = new ButtonGroup();
        group.add(jsonButton);
        group.add(formButton);
        jsonButton.addActionListener(event -> switchTo(View.JSON));
        formButton.addActionListener(event -> switchTo(View.FORM));
        JPanel switcher = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        switcher.add(jsonButton);
        switcher.add(formButton);
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(switcher, BorderLayout.WEST);
        toolbar.add(new ActionLink(MessageBundler.message("dialog.reset"), (ActionListener) event -> reset()),
                BorderLayout.EAST);

        component.add(toolbar, BorderLayout.NORTH);
        component.add(views, BorderLayout.CENTER);
        String notSettable = notSettableProperties();
        if (notSettable != null) {
            // Comments render HTML, so generic types such as Map<String, Object> must be escaped to stay visible.
            component.add(ComponentPanelBuilder.createCommentComponent(StringUtil.escapeXmlEntities(
                    MessageBundler.message("dialog.notSettable", notSettable)), true), BorderLayout.SOUTH);
        }

        if (View.FORM.name().equals(storage.getValue(storageKey + VIEW_SUFFIX))) {
            try {
                showForm(read(jsonEditor.getText()));
                view = View.FORM;
            } catch (JsonBindingException e) {
                // The remembered JSON no longer parses; show it as text so it can be fixed or reset.
            }
        }
        (view == View.JSON ? jsonButton : formButton).setSelected(true);
        cards.show(views, view.name());
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
    public boolean isTall() {
        return true;
    }

    @Override
    public @Nullable ValidationInfo validate() {
        bound = null;
        JsonNode json;
        if (view == View.FORM) {
            ValidationInfo problem = form.validate();
            if (problem != null) {
                return problem;
            }
            json = form.toJson();
        } else {
            try {
                json = read(jsonEditor.getText());
            } catch (JsonBindingException e) {
                return problemIn(e);
            }
        }
        try {
            bound = JsonBinder.bind(json, type);
            return null;
        } catch (JsonBindingException e) {
            return problemIn(e);
        }
    }

    @Override
    public ValueSpec toValueSpec() {
        return bound;
    }

    @Override
    public void save() {
        String json = view == View.FORM ? JsonWriter.write(form.toJson()) : jsonEditor.getText();
        storage.setValue(storageKey + JSON_SUFFIX, json);
        storage.setValue(storageKey + VIEW_SUFFIX, view.name());
    }

    private void switchTo(View target) {
        if (target == view) {
            return;
        }
        if (target == View.FORM) {
            try {
                showForm(read(jsonEditor.getText()));
            } catch (JsonBindingException e) {
                jsonButton.setSelected(true);
                errorReporter.accept(problemIn(e));
                return;
            }
        } else {
            ValidationInfo problem = form.validate();
            if (problem != null) {
                formButton.setSelected(true);
                errorReporter.accept(problem);
                return;
            }
            jsonEditor.setText(JsonWriter.write(form.toJson()));
        }
        view = target;
        cards.show(views, view.name());
        errorReporter.accept(null);
    }

    private void reset() {
        if (view == View.FORM) {
            showForm(JsonSkeleton.of(type));
        } else {
            jsonEditor.setText(skeleton());
        }
    }

    private void showForm(JsonNode json) {
        form = FormEditor.create(type, json);
        formHolder.removeAll();
        formHolder.add(form.component(), BorderLayout.NORTH);
        formHolder.revalidate();
        formHolder.repaint();
    }

    private JsonNode read(String text) throws JsonBindingException {
        return ReadAction.compute(() -> JsonPsiReader.read(project, text));
    }

    private String skeleton() {
        return JsonWriter.write(JsonSkeleton.of(type));
    }

    /** A validation problem pointing at the JSON editor, with the caret moved to where the problem is. */
    private ValidationInfo problemIn(JsonBindingException problem) {
        String message = StringUtil.escapeXmlEntities(InputMessages.describe(problem, parameter.getName()));
        if (view == View.FORM) {
            return new ValidationInfo(message, formScroll);
        }
        Editor editor = jsonEditor.getEditor();
        if (editor != null && problem.getOffset() >= 0 && problem.getOffset() <= editor.getDocument().getTextLength()) {
            editor.getCaretModel().moveToOffset(problem.getOffset());
            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
        return new ValidationInfo(message, jsonEditor);
    }

    /** The properties of the bean (or of the list's beans) that can't be entered yet, or null if there are none. */
    private @Nullable String notSettableProperties() {
        TypeDescriptor bean = type instanceof TypeDescriptor.SequenceType
                ? ((TypeDescriptor.SequenceType) type).getElementType()
                : type;
        if (!(bean instanceof TypeDescriptor.BeanType)) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (PropertyDescriptor property : ((TypeDescriptor.BeanType) bean).getProperties()) {
            if (!property.getType().isSupported()) {
                names.add(property.getName() + " (" + property.getType().getDisplayName() + ")");
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }
}
package com.zapryan.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.zapryan.launch.MethodInput;
import com.zapryan.reflection.ConstructorDescriptor;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects the values to call a method with: a constructor and its arguments for the receiver of an instance
 * method, then the method's own arguments. Values are remembered per parameter across runs. For a generated test it
 * also offers to write the test without running the method.
 */
final class MethodInputDialog extends DialogWrapper {

    private static final String KEY_PREFIX = "instarun.input.";
    private static final String WITHOUT_RUNNING_KEY = "instarun.generate.withoutRunning";

    private final Project project;
    private final MethodDescriptor method;
    private final PropertiesComponent storage;
    private final List<ValueEditor> argumentFields;
    private final @Nullable ComboBox<ConstructorDescriptor> constructorBox;
    private final Map<ConstructorDescriptor, List<ValueEditor>> constructorFields = new HashMap<>();
    private final JPanel root = new JPanel(new BorderLayout());
    /** Only for a generated test: ticked to write the test without running the method. */
    private final @Nullable JBCheckBox withoutRunningBox;

    /**
     * The inputs for a generated test, and whether to run the method first so the test can check what it did.
     *
     * @param runMethod false when the user chose to write the test without running the method
     */
    record TestInput(MethodInput input, boolean runMethod) {
    }

    /**
     * Returns the values to call the method with, or null if the user cancelled. Shows the dialog only when there
     * is something to enter or choose; a runnable method is expected, i.e. one with no {@code RunBlocker}.
     *
     * @param okText the button that confirms, e.g. Run
     */
    static @Nullable MethodInput ask(Project project, MethodDescriptor method, String title, String okText) {
        List<ConstructorDescriptor> constructors = constructorsFor(method);
        boolean nothingToChoose = constructors.isEmpty()
                || constructors.size() == 1 && constructors.get(0).getParameters().isEmpty();
        if (method.getParameters().isEmpty() && nothingToChoose) {
            ValueSpec receiver = constructors.isEmpty()
                    ? null
                    : new ValueSpec.Constructed(method.getDeclaringClassBinaryName(), List.of(), List.of());
            return new MethodInput(receiver, List.of());
        }
        MethodInputDialog dialog = new MethodInputDialog(project, method, constructors, title, okText, false);
        return dialog.showAndGet() ? dialog.input() : null;
    }

    /**
     * Like {@link #ask}, for generating a test. The dialog also offers to write the test without running the method,
     * so it is shown even when there is nothing to enter; the choice is remembered for the next test.
     */
    static @Nullable TestInput askForTest(Project project, MethodDescriptor method, String title, String okText) {
        MethodInputDialog dialog = new MethodInputDialog(project, method, constructorsFor(method), title, okText, true);
        return dialog.showAndGet() ? new TestInput(dialog.input(), dialog.runsMethod()) : null;
    }

    private MethodInputDialog(Project project, MethodDescriptor method, List<ConstructorDescriptor> constructors,
                              String title, String okText, boolean forTest) {
        super(project);
        this.project = project;
        this.method = method;
        this.storage = PropertiesComponent.getInstance(project);
        this.argumentFields = fieldsFor(method.getParameters(), methodKey());
        this.constructorBox = constructors.isEmpty() ? null : createConstructorBox(constructors);
        this.withoutRunningBox = forTest ? createWithoutRunningBox() : null;
        setTitle(title);
        setOKButtonText(okText);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        layoutFields();
        // Several bean parameters can be taller than the screen; the dialog then scrolls instead of cutting them off.
        JComponent fields = ScrollPaneFactory.createScrollPane(root, true);
        if (withoutRunningBox == null) {
            return fields;
        }
        // Below the fields rather than among them, so it stays in view however far they scroll.
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.add(fields, BorderLayout.CENTER);
        panel.add(withoutRunningBox, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    protected @NotNull List<ValidationInfo> doValidateAll() {
        List<ValidationInfo> problems = new ArrayList<>();
        for (ValueEditor field : visibleFields()) {
            ValidationInfo problem = field.validate();
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems;
    }

    @Override
    protected void doOKAction() {
        visibleFields().forEach(ValueEditor::save);
        if (constructorBox != null) {
            storage.setValue(selectedConstructorKey(), signatureOf(selectedConstructor().getParameters()));
        }
        if (withoutRunningBox != null) {
            storage.setValue(WITHOUT_RUNNING_KEY, withoutRunningBox.isSelected());
        }
        super.doOKAction();
    }

    private boolean runsMethod() {
        return withoutRunningBox == null || !withoutRunningBox.isSelected();
    }

    private MethodInput input() {
        ValueSpec receiver = null;
        if (constructorBox != null) {
            ConstructorDescriptor constructor = selectedConstructor();
            receiver = new ValueSpec.Constructed(method.getDeclaringClassBinaryName(),
                    erasedTypesOf(constructor.getParameters()), valuesOf(selectedConstructorFields()));
        }
        return new MethodInput(receiver, valuesOf(argumentFields));
    }

    private JBCheckBox createWithoutRunningBox() {
        return new JBCheckBox(MessageBundler.message("dialog.generate.withoutRunning"),
                storage.getBoolean(WITHOUT_RUNNING_KEY));
    }

    private static List<ConstructorDescriptor> constructorsFor(MethodDescriptor method) {
        return method.isStatic() ? List.of() : method.getReceiver().getUsableConstructors();
    }

    private ComboBox<ConstructorDescriptor> createConstructorBox(List<ConstructorDescriptor> constructors) {
        ComboBox<ConstructorDescriptor> box = new ComboBox<>(constructors.toArray(new ConstructorDescriptor[0]));
        box.setRenderer(SimpleListCellRenderer.create("", this::describe));
        box.setSelectedItem(initialConstructor(constructors));
        box.addActionListener(event -> {
            layoutFields();
            pack();
        });
        return box;
    }

    private void layoutFields() {
        FormBuilder form = FormBuilder.createFormBuilder();
        if (constructorBox != null) {
            form.addComponent(new TitledSeparator(MessageBundler.message("dialog.receiver", className())));
            form.addLabeledComponent(MessageBundler.message("dialog.constructor"), constructorBox);
            addFields(form, selectedConstructorFields());
        }
        if (!argumentFields.isEmpty()) {
            form.addComponent(new TitledSeparator(MessageBundler.message("dialog.arguments", method.getMethodName())));
            addFields(form, argumentFields);
        }
        root.removeAll();
        root.add(form.getPanel(), BorderLayout.CENTER);
        root.revalidate();
    }

    private static void addFields(FormBuilder form, List<ValueEditor> fields) {
        for (ValueEditor field : fields) {
            form.addLabeledComponent(field.label(), field.component(), field.isTall());
        }
    }

    private List<ValueEditor> visibleFields() {
        List<ValueEditor> fields = new ArrayList<>();
        if (constructorBox != null) {
            fields.addAll(selectedConstructorFields());
        }
        fields.addAll(argumentFields);
        return fields;
    }

    private List<ValueEditor> selectedConstructorFields() {
        return constructorFields.computeIfAbsent(selectedConstructor(),
                constructor -> fieldsFor(constructor.getParameters(), constructorKey(constructor)));
    }

    private ConstructorDescriptor selectedConstructor() {
        return constructorBox.getItemAt(constructorBox.getSelectedIndex());
    }

    private ConstructorDescriptor initialConstructor(List<ConstructorDescriptor> constructors) {
        String remembered = storage.getValue(selectedConstructorKey());
        for (ConstructorDescriptor constructor : constructors) {
            if (signatureOf(constructor.getParameters()).equals(remembered)) {
                return constructor;
            }
        }
        for (ConstructorDescriptor constructor : constructors) {
            if (constructor.getParameters().isEmpty()) {
                return constructor;
            }
        }
        return constructors.get(0);
    }

    private List<ValueEditor> fieldsFor(List<ParameterDescriptor> parameters, String keyPrefix) {
        return parameters.stream()
                .map(parameter -> ValueEditor.forParameter(parameter, project, storage, keyPrefix + parameter.getName(),
                        problem -> setErrorInfoAll(problem != null ? List.of(problem) : List.of())))
                .toList();
    }

    private String describe(ConstructorDescriptor constructor) {
        String parameters = constructor.getParameters().stream()
                .map(parameter -> parameter.getType().getDisplayName() + " " + parameter.getName())
                .collect(Collectors.joining(", "));
        return className() + "(" + parameters + ")";
    }

    private String className() {
        return StringUtil.getShortName(method.getDeclaringClassFqn());
    }

    private String methodKey() {
        return KEY_PREFIX + method.getDeclaringClassBinaryName() + "#" + method.getMethodName()
                + "(" + signatureOf(method.getParameters()) + ").";
    }

    private String constructorKey(ConstructorDescriptor constructor) {
        return selectedConstructorKey() + "(" + signatureOf(constructor.getParameters()) + ").";
    }

    private String selectedConstructorKey() {
        return KEY_PREFIX + method.getDeclaringClassBinaryName() + "#<init>";
    }

    private static String signatureOf(List<ParameterDescriptor> parameters) {
        return parameters.stream().map(ParameterDescriptor::getTypeFqn).collect(Collectors.joining(","));
    }

    private static List<String> erasedTypesOf(List<ParameterDescriptor> parameters) {
        return parameters.stream().map(ParameterDescriptor::getErasedTypeName).toList();
    }

    private static List<ValueSpec> valuesOf(List<ValueEditor> fields) {
        return fields.stream().map(ValueEditor::toValueSpec).toList();
    }
}
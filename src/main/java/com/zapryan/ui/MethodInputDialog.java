package com.zapryan.ui;

import com.intellij.execution.Executor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.FormBuilder;
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
 * method, then the method's own arguments. Values are remembered per parameter across runs.
 */
final class MethodInputDialog extends DialogWrapper {

    private static final String KEY_PREFIX = "instarun.input.";

    private final MethodDescriptor method;
    private final PropertiesComponent storage;
    private final List<ArgumentField> argumentFields;
    private final @Nullable ComboBox<ConstructorDescriptor> constructorBox;
    private final Map<ConstructorDescriptor, List<ArgumentField>> constructorFields = new HashMap<>();
    private final JPanel root = new JPanel(new BorderLayout());

    /**
     * Returns the values to call the method with, or null if the user cancelled. Shows the dialog only when there
     * is something to enter or choose; a runnable method is expected, i.e. one with no {@code RunBlocker}.
     */
    static @Nullable MethodInput ask(Project project, MethodDescriptor method, Executor executor) {
        List<ConstructorDescriptor> constructors =
                method.isStatic() ? List.of() : method.getReceiver().getUsableConstructors();
        boolean nothingToChoose = constructors.isEmpty()
                || constructors.size() == 1 && constructors.get(0).getParameters().isEmpty();
        if (method.getParameters().isEmpty() && nothingToChoose) {
            ValueSpec receiver = constructors.isEmpty()
                    ? null
                    : new ValueSpec.Constructed(method.getDeclaringClassBinaryName(), List.of(), List.of());
            return new MethodInput(receiver, List.of());
        }
        MethodInputDialog dialog = new MethodInputDialog(project, method, constructors, executor);
        return dialog.showAndGet() ? dialog.input() : null;
    }

    private MethodInputDialog(Project project, MethodDescriptor method, List<ConstructorDescriptor> constructors,
                              Executor executor) {
        super(project);
        this.method = method;
        this.storage = PropertiesComponent.getInstance(project);
        this.argumentFields = fieldsFor(method.getParameters(), methodKey());
        this.constructorBox = constructors.isEmpty() ? null : createConstructorBox(constructors);
        setTitle(MessageBundler.message("dialog.title", executor.getActionName(), className(), method.getMethodName()));
        setOKButtonText(executor.getActionName());
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        layoutFields();
        return root;
    }

    @Override
    protected @NotNull List<ValidationInfo> doValidateAll() {
        List<ValidationInfo> problems = new ArrayList<>();
        for (ArgumentField field : visibleFields()) {
            ValidationInfo problem = field.validate();
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems;
    }

    @Override
    protected void doOKAction() {
        visibleFields().forEach(ArgumentField::save);
        if (constructorBox != null) {
            storage.setValue(selectedConstructorKey(), signatureOf(selectedConstructor().getParameters()));
        }
        super.doOKAction();
    }

    private MethodInput input() {
        ValueSpec receiver = null;
        if (constructorBox != null) {
            ConstructorDescriptor constructor = selectedConstructor();
            receiver = new ValueSpec.Constructed(method.getDeclaringClassBinaryName(),
                    typesOf(constructor.getParameters()), valuesOf(selectedConstructorFields()));
        }
        return new MethodInput(receiver, valuesOf(argumentFields));
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

    private static void addFields(FormBuilder form, List<ArgumentField> fields) {
        for (ArgumentField field : fields) {
            form.addLabeledComponent(field.label(), field.component());
        }
    }

    private List<ArgumentField> visibleFields() {
        List<ArgumentField> fields = new ArrayList<>();
        if (constructorBox != null) {
            fields.addAll(selectedConstructorFields());
        }
        fields.addAll(argumentFields);
        return fields;
    }

    private List<ArgumentField> selectedConstructorFields() {
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

    private List<ArgumentField> fieldsFor(List<ParameterDescriptor> parameters, String keyPrefix) {
        return parameters.stream()
                .map(parameter -> new ArgumentField(parameter, storage, keyPrefix + parameter.getName()))
                .toList();
    }

    private String describe(ConstructorDescriptor constructor) {
        String parameters = constructor.getParameters().stream()
                .map(parameter -> StringUtil.getShortName(parameter.getTypeFqn()) + " " + parameter.getName())
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
        return String.join(",", typesOf(parameters));
    }

    private static List<String> typesOf(List<ParameterDescriptor> parameters) {
        return parameters.stream().map(ParameterDescriptor::getTypeFqn).toList();
    }

    private static List<ValueSpec> valuesOf(List<ArgumentField> fields) {
        return fields.stream().map(ArgumentField::toValueSpec).toList();
    }
}
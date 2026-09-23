package com.zapryan.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ParameterDescriptor;
import lombok.Getter;

import java.awt.GridLayout;
import java.util.List;

/**
 * Showcase panel: prints the pieces of a {@link MethodDescriptor} needed to invoke the method
 * via reflection (declaring class, name, return type, static/instance, parameter types).
 */
@Getter
public class MethodInfoPanel {

    private final JBPanel<JBPanel<?>> content;

    public MethodInfoPanel(MethodDescriptor descriptor) {
        content = new JBPanel<>(new GridLayout(0, 1, 0, 4));
        content.setBorder(JBUI.Borders.empty(12));

        content.add(new JBLabel(MessageBundler.message("methodinfo.declaringClass.label", descriptor.getDeclaringClassFqn())));
        content.add(new JBLabel(MessageBundler.message("methodinfo.methodName.label", descriptor.getMethodName())));
        content.add(new JBLabel(MessageBundler.message("methodinfo.returnType.label", descriptor.getReturnTypeFqn())));
        content.add(new JBLabel(MessageBundler.message("methodinfo.static.label", descriptor.isStatic())));

        List<ParameterDescriptor> parameters = descriptor.getParameters();
        content.add(new JBLabel(MessageBundler.message("methodinfo.parameterCount.label", parameters.size())));

        if (parameters.isEmpty()) {
            content.add(new JBLabel(MessageBundler.message("methodinfo.noParameters.label")));
        } else {
            for (int i = 0; i < parameters.size(); i++) {
                ParameterDescriptor parameter = parameters.get(i);
                content.add(new JBLabel(MessageBundler.message(
                        "methodinfo.parameter.label", i, parameter.getTypeFqn(), parameter.getName())));
            }
        }
    }
}

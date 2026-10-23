package com.zapryan.ui;


import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiMethod;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.MethodDescriptorFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

// TODO to remove later
public class InstaRunAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = MethodAtCaret.find(e) != null;
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        MethodDescriptor descriptor = ReadAction.compute(() -> {
            PsiMethod method = MethodAtCaret.find(e);
            return method != null ? MethodDescriptorFactory.from(method) : null;
        });
        if (descriptor == null) {
            return;
        }

        MethodInfoPanel methodInfoPanel = new MethodInfoPanel(descriptor);

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(methodInfoPanel.getContent(), null)
                .setTitle("InstaRun Window From Right Click")
                .setMovable(true)
                .setResizable(true)
                .setMinSize(new Dimension(280, 160))
                .createPopup();

        popup.showCenteredInCurrentWindow(project);
    }
}
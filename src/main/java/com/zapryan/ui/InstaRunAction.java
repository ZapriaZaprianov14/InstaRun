package com.zapryan.ui;


import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.MethodDescriptorFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class InstaRunAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = resolveMethodNameAtCaret(e) != null;
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private PsiMethod resolveMethodNameAtCaret(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) {
            return null;
        }
        Caret caret = editor.getCaretModel().getCurrentCaret();
        int offset = caret.getOffset();
        PsiElement elementAtCaret = psiFile.findElementAt(offset);
        if (elementAtCaret == null) {
            return null;
        }
        PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class);
        if (method == null) {
            return null;
        }
        PsiIdentifier nameIdentifier = method.getNameIdentifier();
        return nameIdentifier != null && nameIdentifier.equals(elementAtCaret) ? method : null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        MethodDescriptor descriptor = ReadAction.compute(() -> {
            PsiMethod method = resolveMethodNameAtCaret(e);
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

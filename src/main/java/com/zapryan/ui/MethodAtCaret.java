package com.zapryan.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MethodAtCaret {

    private MethodAtCaret() {
    }

    /**
     * Returns the method whose name identifier the caret is on, or null anywhere else. Needs read access.
     */
    static @Nullable PsiMethod find(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) {
            return null;
        }
        PsiElement elementAtCaret = psiFile.findElementAt(editor.getCaretModel().getOffset());
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
}
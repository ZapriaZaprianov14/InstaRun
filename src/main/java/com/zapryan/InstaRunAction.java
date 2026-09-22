package com.zapryan;


import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class InstaRunAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = isMethodUnderCaret(e);
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private boolean isMethodUnderCaret(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (editor == null || psiFile == null) {
            return false;
        }

        Caret caret = editor.getCaretModel().getCurrentCaret();
        int offset = caret.getOffset();

        PsiElement elementAtCaret = psiFile.findElementAt(offset);
        if (elementAtCaret == null) {
            return false;
        }

        PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class);
        return method != null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        MyToolWindowJava myToolWindowJava =
                new MyToolWindowJava();

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(myToolWindowJava.getContent(), null)
                .setTitle("InstaRun Window From Right Click")
                .setMovable(true)
                .setResizable(true)
                .setMinSize(new Dimension(220, 100))
                .createPopup();

//        myToolWindowJava.getShuffleButton().addActionListener(event -> {
//            myToolWindowJava.getContent().revalidate();
//            myToolWindowJava.getContent().repaint();
//            popup.pack(true, true);
//        });

        popup.showCenteredInCurrentWindow(project);
    }
}

package com.zapryan.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.zapryan.launch.LaunchTarget;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.RunBlocker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An action on the method whose name the caret is on, such as Run Method. It is shown on any method name, and when
 * chosen either says exactly why InstaRun can't call the method, or goes on with {@link #perform}.
 */
abstract class MethodAtCaretAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(methodAt(e) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        Found found = ReadAction.compute(() -> {
            PsiMethod method = methodAt(e);
            if (method == null) {
                return null;
            }
            return new Found(LaunchTarget.of(method), SmartPointerManager.createPointer(method));
        });
        if (found == null) {
            return;
        }
        MethodDescriptor method = found.target().getMethod();
        List<RunBlocker> blockers = RunBlocker.findAll(method);
        if (!blockers.isEmpty()) {
            showError(editor, InputMessages.whyBlocked(method, blockers));
            return;
        }
        if (found.target().getModule() == null) {
            showError(editor, MessageBundler.message("run.error.noModule", method.getMethodName()));
            return;
        }
        perform(project, editor, found.target(), found.pointer());
    }

    /**
     * Goes on with a method InstaRun can call, whose module is known. Called on the event dispatch thread.
     *
     * @param pointer the method, for what still needs its PSI after a dialog
     */
    abstract void perform(Project project, Editor editor, LaunchTarget target,
                          SmartPsiElementPointer<PsiMethod> pointer);

    /** The method whose name identifier the caret is on, or null anywhere else. Needs read access. */
    static @Nullable PsiMethod methodAt(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) {
            return null;
        }
        PsiElement elementAtCaret = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class);
        PsiIdentifier nameIdentifier = method != null ? method.getNameIdentifier() : null;
        return nameIdentifier != null && nameIdentifier.equals(elementAtCaret) ? method : null;
    }

    static void showError(Editor editor, String message) {
        // The hint renders HTML, so generic types such as List<String> must be escaped to stay visible.
        HintManager.getInstance().showErrorHint(editor, StringUtil.escapeXmlEntities(message));
    }

    /** What the action learns about the method in one read action. */
    private record Found(LaunchTarget target, SmartPsiElementPointer<PsiMethod> pointer) {
    }
}
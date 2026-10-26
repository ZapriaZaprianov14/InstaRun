package com.zapryan.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.Executor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.zapryan.launch.LaunchTarget;
import com.zapryan.launch.MethodInput;
import com.zapryan.launch.MethodLauncher;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.RunBlocker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shared behaviour of Run Method and Debug Method: visible on any method name, and on click either
 * reports exactly why the method cannot run, or asks for its inputs and launches it under this action's executor.
 */
abstract class MethodLaunchAction extends AnAction {

    private final Supplier<Executor> executor;

    MethodLaunchAction(Supplier<Executor> executor) {
        this.executor = executor;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(MethodAtCaret.find(e) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        LaunchTarget target = ReadAction.compute(() -> {
            PsiMethod method = MethodAtCaret.find(e);
            return method != null ? LaunchTarget.of(method) : null;
        });
        if (target == null) {
            return;
        }

        MethodDescriptor method = target.getMethod();
        List<RunBlocker> blockers = RunBlocker.findAll(method);
        if (!blockers.isEmpty()) {
            showError(editor, InputMessages.whyBlocked(method, blockers));
            return;
        }
        Module module = target.getModule();
        if (module == null) {
            showError(editor, MessageBundler.message("run.error.noModule", method.getMethodName()));
            return;
        }
        Executor chosenExecutor = executor.get();
        MethodInput input = MethodInputDialog.ask(project, method,
                MessageBundler.message("dialog.title", chosenExecutor.getActionName(),
                        StringUtil.getShortName(method.getDeclaringClassFqn()), method.getMethodName()),
                chosenExecutor.getActionName());
        if (input == null) {
            return;
        }
        MethodLauncher.launch(project, module, target.isInTestSources(), method, input, chosenExecutor);
    }

    static void showError(Editor editor, String message) {
        // The hint renders HTML, so generic types such as List<String> must be escaped to stay visible.
        HintManager.getInstance().showErrorHint(editor, StringUtil.escapeXmlEntities(message));
    }
}
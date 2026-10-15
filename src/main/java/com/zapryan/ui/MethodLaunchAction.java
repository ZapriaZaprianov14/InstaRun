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
import com.intellij.psi.PsiMethod;
import com.zapryan.launch.LaunchTarget;
import com.zapryan.launch.MethodLauncher;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.RunBlocker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Shared behaviour of Run Method and Debug Method: visible on any method name, and on click either
 * reports exactly why the method cannot run or launches it under this action's executor.
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
            showError(editor, MessageBundler.message("run.blocked", method.getMethodName(), describeAll(blockers)));
            return;
        }
        Module module = target.getModule();
        if (module == null) {
            showError(editor, MessageBundler.message("run.error.noModule", method.getMethodName()));
            return;
        }
        MethodLauncher.launch(project, module, target.isInTestSources(), method, executor.get());
    }

    private static String describeAll(List<RunBlocker> blockers) {
        return blockers.stream().map(MethodLaunchAction::describe).collect(Collectors.joining(" "));
    }

    private static String describe(RunBlocker blocker) {
        return switch (blocker) {
            case CONSTRUCTOR -> MessageBundler.message("run.blocker.constructor");
            case LOCAL_OR_ANONYMOUS_CLASS -> MessageBundler.message("run.blocker.localOrAnonymousClass");
            case NOT_STATIC -> MessageBundler.message("run.blocker.notStatic");
            case HAS_PARAMETERS -> MessageBundler.message("run.blocker.hasParameters");
        };
    }

    private static void showError(Editor editor, String message) {
        HintManager.getInstance().showErrorHint(editor, message);
    }
}
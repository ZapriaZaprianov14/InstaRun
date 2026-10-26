package com.zapryan.ui;

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
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.zapryan.bootstrap.Outcome;
import com.zapryan.codegen.TestGenerator;
import com.zapryan.launch.LaunchTarget;
import com.zapryan.launch.MethodInput;
import com.zapryan.launch.MethodLauncher;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.RunBlocker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Generate Unit Test: asks for the method's inputs like Run Method, runs it once, and writes a JUnit test that makes the
 * same call and checks it returns or throws what it did. Anything that keeps the call from becoming a test is reported
 * before the run, so nothing runs for nothing.
 */
public final class GenerateTestAction extends AnAction {

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
        Target target = ReadAction.compute(() -> {
            PsiMethod psiMethod = MethodAtCaret.find(e);
            if (psiMethod == null) {
                return null;
            }
            LaunchTarget launchTarget = LaunchTarget.of(psiMethod);
            Module module = launchTarget.getModule();
            String problem = module != null ? TestGenerator.checkMethod(project, module, psiMethod) : null;
            return new Target(launchTarget, SmartPointerManager.createPointer(psiMethod), problem);
        });
        if (target == null) {
            return;
        }

        MethodDescriptor method = target.launchTarget().getMethod();
        List<RunBlocker> blockers = RunBlocker.findAll(method);
        if (!blockers.isEmpty()) {
            MethodLaunchAction.showError(editor, InputMessages.whyBlocked(method, blockers));
            return;
        }
        Module module = target.launchTarget().getModule();
        if (module == null) {
            MethodLaunchAction.showError(editor, MessageBundler.message("run.error.noModule", method.getMethodName()));
            return;
        }
        if (target.problem() != null) {
            MethodLaunchAction.showError(editor, target.problem());
            return;
        }
        MethodInput input = MethodInputDialog.ask(project, method,
                MessageBundler.message("dialog.generate.title", StringUtil.getShortName(method.getDeclaringClassFqn()),
                        method.getMethodName()),
                MessageBundler.message("dialog.generate.ok"));
        if (input == null) {
            return;
        }
        String inputProblem = ReadAction.compute(() -> {
            PsiMethod psiMethod = target.pointer().getElement();
            return psiMethod != null ? TestGenerator.checkInputs(project, psiMethod, input) : null;
        });
        if (inputProblem != null) {
            MethodLaunchAction.showError(editor, inputProblem);
            return;
        }

        try {
            MethodLauncher.launchRecording(project, module, target.launchTarget().isInTestSources(), method, input,
                    new MethodLauncher.RecordingListener() {
                        @Override
                        public void ended(@Nullable Outcome outcome) {
                            TestGenerator.generate(project, module, target.pointer(), method, input, outcome);
                        }

                        @Override
                        public void notStarted() {
                            TestGenerator.notify(project,
                                    MessageBundler.message("generate.failed.notStarted", method.getMethodName()));
                        }
                    });
        } catch (IOException ex) {
            MethodLaunchAction.showError(editor,
                    MessageBundler.message("generate.failed.write", method.getMethodName(), ex.getMessage()));
        }
    }

    /** What the action learns about the method in one read action. */
    private record Target(LaunchTarget launchTarget, SmartPsiElementPointer<PsiMethod> pointer,
                          @Nullable String problem) {
    }
}
package com.instarun.ui;

import com.intellij.execution.Executor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;
import com.instarun.launch.LaunchTarget;
import com.instarun.launch.MethodInput;
import com.instarun.launch.MethodLauncher;
import com.instarun.reflection.MethodDescriptor;

import java.util.function.Supplier;

/** Run Method and Debug Method: ask for the method's inputs and launch it under this action's executor. */
abstract class MethodLaunchAction extends MethodAtCaretAction {

    private final Supplier<Executor> executor;

    MethodLaunchAction(Supplier<Executor> executor) {
        this.executor = executor;
    }

    @Override
    void perform(Project project, Editor editor, LaunchTarget target, SmartPsiElementPointer<PsiMethod> pointer) {
        MethodDescriptor method = target.getMethod();
        Executor chosenExecutor = executor.get();
        MethodInput input = MethodInputDialog.ask(project, method,
                MessageBundler.message("dialog.title", chosenExecutor.getActionName(),
                        StringUtil.getShortName(method.getDeclaringClassFqn()), method.getMethodName()),
                chosenExecutor.getActionName());
        if (input != null) {
            MethodLauncher.launch(project, target, input, chosenExecutor);
        }
    }
}
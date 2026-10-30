package com.zapryan.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;
import com.zapryan.bootstrap.Outcome;
import com.zapryan.codegen.TestGenerator;
import com.zapryan.launch.LaunchTarget;
import com.zapryan.launch.MethodInput;
import com.zapryan.launch.MethodLauncher;
import com.zapryan.reflection.MethodDescriptor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Function;

/**
 * Generate Unit Test: asks for the method's inputs like Run Method, runs it once, and writes a JUnit test that makes
 * the same call and checks it returns or throws what it did. Anything that keeps the call from becoming a test is
 * reported before the run, so nothing runs for nothing. The user can instead have the test written without running
 * the method, e.g. for one that changes data or calls other systems; it then only makes the call, and the checks are
 * left to them.
 */
public final class GenerateTestAction extends MethodAtCaretAction {

    @Override
    void perform(Project project, Editor editor, LaunchTarget target, SmartPsiElementPointer<PsiMethod> pointer) {
        MethodDescriptor method = target.getMethod();
        Module module = target.getModule();
        String problem = problemWith(pointer, psiMethod -> TestGenerator.checkMethod(project, module, psiMethod));
        if (problem != null) {
            showError(editor, problem);
            return;
        }
        MethodInputDialog.TestInput choice = MethodInputDialog.askForTest(project, method,
                MessageBundler.message("dialog.generate.title", StringUtil.getShortName(method.getDeclaringClassFqn()),
                        method.getMethodName()),
                MessageBundler.message("dialog.generate.ok"));
        if (choice == null) {
            return;
        }
        MethodInput input = choice.input();
        String inputProblem = problemWith(pointer, psiMethod -> TestGenerator.checkInputs(project, psiMethod, input));
        if (inputProblem != null) {
            showError(editor, inputProblem);
            return;
        }
        if (!choice.runMethod()) {
            TestGenerator.generateWithoutRunning(project, module, pointer, method, input);
            return;
        }

        try {
            MethodLauncher.launchRecording(project, target, input, new MethodLauncher.RecordingListener() {
                @Override
                public void ended(@Nullable Outcome outcome) {
                    TestGenerator.generate(project, module, pointer, method, input, outcome);
                }

                @Override
                public void notStarted() {
                    TestGenerator.notify(project,
                            MessageBundler.message("generate.failed.notStarted", method.getMethodName()));
                }
            });
        } catch (IOException e) {
            showError(editor, MessageBundler.message("generate.failed.write", method.getMethodName(), e.getMessage()));
        }
    }

    /**
     * What the check finds wrong with the method, or null. A method deleted meanwhile passes here; writing the test
     * reports it.
     */
    private static @Nullable String problemWith(SmartPsiElementPointer<PsiMethod> pointer,
                                                Function<PsiMethod, @Nullable String> check) {
        return ReadAction.compute(() -> {
            PsiMethod method = pointer.getElement();
            return method != null ? check.apply(method) : null;
        });
    }
}
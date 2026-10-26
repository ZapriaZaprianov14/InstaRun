package com.zapryan.codegen;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A {@link CodeContext} answered from PSI, for a test in the given package. Needs a read action. */
final class PsiCodeContext implements CodeContext {

    private final PsiManager manager;
    private final String testPackage;

    PsiCodeContext(Project project, String testPackage) {
        this.manager = PsiManager.getInstance(project);
        this.testPackage = testPackage;
    }

    @Override
    public @Nullable String referableName(String classBinaryName) {
        PsiClass aClass = ClassUtil.findPsiClass(manager, classBinaryName);
        return aClass != null && Visibility.isAccessible(aClass, testPackage) ? aClass.getQualifiedName() : null;
    }

    @Override
    public boolean isOverloaded(String classBinaryName, String methodName, int parameterCount) {
        PsiClass aClass = ClassUtil.findPsiClass(manager, classBinaryName);
        if (aClass == null) {
            return false;
        }
        PsiMethod[] candidates =
                "<init>".equals(methodName) ? aClass.getConstructors() : aClass.findMethodsByName(methodName, true);
        // An overriding method and the one it overrides have the same erased parameters, so they count once.
        Set<String> signatures = new HashSet<>();
        for (PsiMethod candidate : candidates) {
            if (candidate.getParameterList().getParametersCount() == parameterCount) {
                signatures.add(Arrays.stream(candidate.getParameterList().getParameters())
                        .map(PsiParameter::getType)
                        .map(type -> TypeConversionUtil.erasure(type).getCanonicalText())
                        .collect(Collectors.joining(",")));
            }
        }
        return signatures.size() > 1;
    }

    @Override
    public TestComments comments() {
        return new TestComments() {
            @Override
            public String recordedValues() {
                return MessageBundler.message("generate.comment.recorded");
            }

            @Override
            public String returnedNothing() {
                return MessageBundler.message("generate.comment.void");
            }

            @Override
            public String moreValuesNotChecked() {
                return MessageBundler.message("generate.comment.more");
            }

            @Override
            public String accessorThrew(String expression, String exceptionClass) {
                return MessageBundler.message("generate.comment.threw", expression, exceptionClass);
            }
        };
    }
}
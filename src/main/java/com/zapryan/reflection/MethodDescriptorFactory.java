package com.zapryan.reflection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@link PsiMethod} into a {@link MethodDescriptor}. Must run inside a read action;
 * the returned descriptor is plain data and safe to use Afterward without one.
 */
public final class MethodDescriptorFactory {

    private MethodDescriptorFactory() {
    }

    public static MethodDescriptor from(PsiMethod method) {
        PsiClass declaringClass = method.getContainingClass();
        String declaringClassFqn = declaringClass != null && declaringClass.getQualifiedName() != null
                ? declaringClass.getQualifiedName()
                : "<unknown>";

        String returnTypeFqn = returnTypeFqn(method, declaringClassFqn);
        boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);

        PsiParameterList parameterList = method.getParameterList();
        List<ParameterDescriptor> parameters = new ArrayList<>(parameterList.getParametersCount());
        for (PsiParameter parameter : parameterList.getParameters()) {
            parameters.add(new ParameterDescriptor(
                    parameter.getName(),
                    parameter.getType().getCanonicalText()));
        }

        return new MethodDescriptor(declaringClassFqn, method.getName(), returnTypeFqn, isStatic, parameters);
    }

    private static String returnTypeFqn(PsiMethod method, String declaringClassFqn) {
        if (method.isConstructor()) {
            return declaringClassFqn;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null ? returnType.getCanonicalText() : "void";
    }
}

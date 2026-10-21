package com.zapryan.reflection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import org.jetbrains.annotations.Nullable;

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
        String declaringClassBinaryName = declaringClass != null ? ClassUtil.getJVMClassName(declaringClass) : null;

        String returnTypeFqn = returnTypeFqn(method, declaringClassFqn);
        boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);

        return new MethodDescriptor(declaringClassFqn, declaringClassBinaryName, method.getName(), returnTypeFqn,
                isStatic, method.isConstructor(), parametersOf(method.getParameterList().getParameters()),
                receiverOf(declaringClass));
    }

    private static String returnTypeFqn(PsiMethod method, String declaringClassFqn) {
        if (method.isConstructor()) {
            return declaringClassFqn;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null ? returnType.getCanonicalText() : "void";
    }

    private static List<ParameterDescriptor> parametersOf(PsiVariable[] variables) {
        List<ParameterDescriptor> parameters = new ArrayList<>(variables.length);
        for (PsiVariable variable : variables) {
            parameters.add(new ParameterDescriptor(variable.getName(), variable.getType().getCanonicalText()));
        }
        return parameters;
    }

    private static ReceiverDescriptor receiverOf(@Nullable PsiClass declaringClass) {
        if (declaringClass == null) {
            return new ReceiverDescriptor(ReceiverDescriptor.Kind.ABSTRACT, List.of());
        }
        ReceiverDescriptor.Kind kind = kindOf(declaringClass);
        List<ConstructorDescriptor> constructors =
                kind == ReceiverDescriptor.Kind.INSTANTIABLE ? constructorsOf(declaringClass) : List.of();
        return new ReceiverDescriptor(kind, constructors);
    }

    private static ReceiverDescriptor.Kind kindOf(PsiClass aClass) {
        if (aClass.isEnum()) {
            return ReceiverDescriptor.Kind.ENUM;
        }
        if (aClass.isInterface() || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return ReceiverDescriptor.Kind.ABSTRACT;
        }
        if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return ReceiverDescriptor.Kind.INNER_CLASS;
        }
        return ReceiverDescriptor.Kind.INSTANTIABLE;
    }

    private static List<ConstructorDescriptor> constructorsOf(PsiClass aClass) {
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
            // The compiler gives a class that declares no constructor a no-argument one.
            return List.of(new ConstructorDescriptor(List.of()));
        }
        List<ConstructorDescriptor> result = new ArrayList<>(constructors.length);
        for (PsiMethod constructor : constructors) {
            // A compact record constructor declares no parameters in source but takes the record components.
            PsiVariable[] parameters = JavaPsiRecordUtil.isCompactConstructor(constructor)
                    ? aClass.getRecordComponents()
                    : constructor.getParameterList().getParameters();
            result.add(new ConstructorDescriptor(parametersOf(parameters)));
        }
        return result;
    }
}
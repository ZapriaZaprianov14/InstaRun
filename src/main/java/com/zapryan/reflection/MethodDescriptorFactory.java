package com.zapryan.reflection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.Nullable;

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

        TypeDescriptorFactory types = new TypeDescriptorFactory();
        List<ParameterDescriptor> parameters =
                types.parametersOf(method.getParameterList().getParameters(), PsiSubstitutor.EMPTY);
        ReceiverDescriptor receiver = receiverOf(declaringClass, types);
        types.settle();

        return new MethodDescriptor(declaringClassFqn, declaringClassBinaryName, method.getName(), returnTypeFqn,
                isStatic, method.isConstructor(), parameters, receiver);
    }

    private static String returnTypeFqn(PsiMethod method, String declaringClassFqn) {
        if (method.isConstructor()) {
            return declaringClassFqn;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null ? returnType.getCanonicalText() : "void";
    }

    private static ReceiverDescriptor receiverOf(@Nullable PsiClass declaringClass, TypeDescriptorFactory types) {
        if (declaringClass == null) {
            return new ReceiverDescriptor(ReceiverDescriptor.Kind.ABSTRACT, List.of());
        }
        ReceiverDescriptor.Kind kind = kindOf(declaringClass);
        List<ConstructorDescriptor> constructors = kind == ReceiverDescriptor.Kind.INSTANTIABLE
                ? types.constructorsOf(declaringClass, PsiSubstitutor.EMPTY)
                : List.of();
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
}
package com.zapryan.reflection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Reads a {@link PsiMethod} into a {@link MethodDescriptor}. Must run inside a read action;
 * the returned descriptor is plain data and safe to use Afterward without one.
 */
public final class MethodDescriptorFactory {

    private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "org.springframework.beans.factory.annotation.Autowired",
            "org.springframework.beans.factory.annotation.Value",
            "javax.inject.Inject",
            "jakarta.inject.Inject",
            "javax.annotation.Resource",
            "jakarta.annotation.Resource");

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
        if (kind == ReceiverDescriptor.Kind.INSTANTIABLE) {
            String springEvidence = springEvidenceOf(declaringClass);
            if (springEvidence != null) {
                return new ReceiverDescriptor(ReceiverDescriptor.Kind.SPRING_BEAN, List.of(), springEvidence);
            }
        }
        List<ConstructorDescriptor> constructors = kind == ReceiverDescriptor.Kind.INSTANTIABLE
                ? types.constructorsOf(declaringClass, PsiSubstitutor.EMPTY)
                : List.of();
        return new ReceiverDescriptor(kind, constructors);
    }

    /**
     * The annotation that shows Spring manages the class, or null if nothing does: a stereotype, i.e. an annotation
     * that is or is built on {@code @Component} such as {@code @Service}, or an injection point on a field, setter or
     * constructor, inherited ones included. A class registered only through a {@code @Bean} method and without
     * injection annotations isn't recognized.
     */
    private static @Nullable String springEvidenceOf(PsiClass aClass) {
        PsiClass stereotype = MetaAnnotationUtil.findAnnotationsByMeta(aClass, List.of(SPRING_COMPONENT))
                .findFirst()
                .orElse(null);
        if (stereotype != null) {
            return "@" + stereotype.getName();
        }
        List<PsiModifierListOwner> injectionPoints = new ArrayList<>(Arrays.asList(aClass.getAllFields()));
        injectionPoints.addAll(Arrays.asList(aClass.getAllMethods()));
        injectionPoints.addAll(Arrays.asList(aClass.getConstructors()));
        for (PsiModifierListOwner member : injectionPoints) {
            PsiAnnotation injection = AnnotationUtil.findAnnotation(member, INJECTION_ANNOTATIONS);
            if (injection != null && injection.getQualifiedName() != null) {
                return "@" + StringUtil.getShortName(injection.getQualifiedName());
            }
        }
        return null;
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
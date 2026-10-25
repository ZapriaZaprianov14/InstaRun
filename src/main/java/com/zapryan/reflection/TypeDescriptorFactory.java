package com.zapryan.reflection;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes PSI types as {@link TypeDescriptor}s. One factory describes one method, so a bean met twice, or inside
 * itself, gets a single descriptor. Must run inside a read action, and {@link #settle()} must be called once
 * everything is described.
 */
final class TypeDescriptorFactory {

    /** Stops runaway generic expansion, e.g. a {@code Node<T>} holding a {@code Node<List<T>>}. */
    private static final int MAX_BEANS = 250;

    /** Beans by their type with type arguments, since {@code Page<Order>} and {@code Page<Item>} differ. */
    private final Map<String, TypeDescriptor.BeanType> beans = new HashMap<>();

    void settle() {
        TypeDescriptor.settle(beans.values());
    }

    List<ParameterDescriptor> parametersOf(PsiVariable[] variables, PsiSubstitutor substitutor) {
        List<ParameterDescriptor> parameters = new ArrayList<>(variables.length);
        for (PsiVariable variable : variables) {
            PsiType declared = variable.getType();
            PsiType actual = substitute(substitutor, declared);
            parameters.add(new ParameterDescriptor(variable.getName(), actual.getCanonicalText(), erasedName(declared),
                    describe(actual)));
        }
        return parameters;
    }

    List<ConstructorDescriptor> constructorsOf(PsiClass aClass, PsiSubstitutor substitutor) {
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
            // The compiler gives a class that declares no constructor a no-argument one.
            return Collections.singletonList(new ConstructorDescriptor(Collections.emptyList()));
        }
        List<ConstructorDescriptor> result = new ArrayList<>(constructors.length);
        for (PsiMethod constructor : constructors) {
            // A compact record constructor declares no parameters in source but takes the record components.
            PsiVariable[] parameters = JavaPsiRecordUtil.isCompactConstructor(constructor)
                    ? aClass.getRecordComponents()
                    : constructor.getParameterList().getParameters();
            result.add(new ConstructorDescriptor(parametersOf(parameters, substitutor)));
        }
        return result;
    }

    TypeDescriptor describe(PsiType type) {
        if (type instanceof PsiPrimitiveType && Literals.isSupported(type.getCanonicalText())) {
            return new TypeDescriptor.LiteralType(type.getCanonicalText(), type.getPresentableText());
        }
        if (type instanceof PsiArrayType) {
            PsiType componentType = ((PsiArrayType) type).getComponentType();
            return new TypeDescriptor.ArrayType(type.getPresentableText(), describe(componentType),
                    erasedName(componentType));
        }
        if (!(type instanceof PsiClassType)) {
            return unsupported(type, "type.unsupported.other");
        }
        PsiClassType classType = (PsiClassType) type;
        PsiClassType.ClassResolveResult resolved = classType.resolveGenerics();
        PsiClass aClass = resolved.getElement();
        if (aClass == null) {
            return unsupported(type, "type.unsupported.unresolved");
        }
        if (aClass instanceof PsiTypeParameter) {
            return unsupported(type, "type.unsupported.typeParameter");
        }
        String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName != null && Literals.isSupported(qualifiedName)) {
            return new TypeDescriptor.LiteralType(qualifiedName, type.getPresentableText());
        }
        String binaryName = ClassUtil.getJVMClassName(aClass);
        if (binaryName == null) {
            return unsupported(type, "type.unsupported.local");
        }
        if (aClass.isEnum()) {
            return new TypeDescriptor.EnumType(type.getPresentableText(), binaryName, constantsOf(aClass));
        }
        String implementation =
                qualifiedName != null ? TypeDescriptor.CollectionType.implementationOf(qualifiedName) : null;
        if (implementation != null) {
            return collection(classType, implementation);
        }
        if (isFromJdk(qualifiedName)) {
            return unsupported(type, "type.unsupported.jdk");
        }
        if (aClass.isInterface() || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return unsupported(type, "type.unsupported.abstract");
        }
        if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return unsupported(type, "type.unsupported.inner");
        }
        if (InheritanceUtil.isInheritor(aClass, "java.util.Collection")
                || InheritanceUtil.isInheritor(aClass, "java.util.Map")) {
            return unsupported(type, "type.unsupported.customCollection");
        }
        return bean(aClass, resolved.getSubstitutor(), classType, binaryName);
    }

    private TypeDescriptor collection(PsiClassType type, String implementation) {
        PsiType[] arguments = type.getParameters();
        if (arguments.length != 1) {
            return unsupported(type, "type.unsupported.rawCollection");
        }
        PsiType elementType = arguments[0];
        if (elementType instanceof PsiWildcardType) {
            PsiWildcardType wildcard = (PsiWildcardType) elementType;
            if (!wildcard.isBounded()) {
                return unsupported(type, "type.unsupported.unboundedElement");
            }
            // Elements of the bound fit both List<? extends Item> and List<? super Item>.
            elementType = wildcard.getBound();
        }
        return new TypeDescriptor.CollectionType(type.getPresentableText(), describe(elementType), implementation);
    }

    private TypeDescriptor bean(PsiClass aClass, PsiSubstitutor substitutor, PsiClassType type, String binaryName) {
        String key = type.getCanonicalText();
        TypeDescriptor.BeanType bean = beans.get(key);
        if (bean != null) {
            return bean;
        }
        if (beans.size() >= MAX_BEANS) {
            return unsupported(type, "type.unsupported.tooMany", MAX_BEANS);
        }
        bean = new TypeDescriptor.BeanType(type.getPresentableText(), binaryName);
        beans.put(key, bean);
        bean.define(constructorsOf(aClass, substitutor), settersOf(aClass, substitutor));
        return bean;
    }

    /**
     * The setters of a class and its superclasses up to the JDK, including private ones and ones generated by Lombok.
     * The most derived setter of a property wins, unless its type can't be entered and another overload's can.
     */
    private List<SetterDescriptor> settersOf(PsiClass aClass, PsiSubstitutor substitutor) {
        Map<String, SetterDescriptor> setters = new LinkedHashMap<>();
        for (PsiClass current = aClass; current != null && !isFromJdk(current.getQualifiedName());
             current = current.getSuperClass()) {
            String declaringClass = ClassUtil.getJVMClassName(current);
            if (declaringClass == null) {
                break;
            }
            PsiSubstitutor currentSubstitutor = current == aClass
                    ? substitutor
                    : TypeConversionUtil.getSuperClassSubstitutor(current, aClass, substitutor);
            for (PsiMethod method : current.getMethods()) {
                String property = SetterDescriptor.propertyNameOf(method.getName());
                if (property == null || method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)
                        || method.getParameterList().getParametersCount() != 1) {
                    continue;
                }
                SetterDescriptor existing = setters.get(property);
                if (existing != null && existing.getType().isSupported()) {
                    continue;
                }
                PsiType declared = method.getParameterList().getParameters()[0].getType();
                SetterDescriptor setter = new SetterDescriptor(property, declaringClass, method.getName(),
                        erasedName(declared), describe(substitute(currentSubstitutor, declared)));
                if (existing == null || setter.getType().isSupported()) {
                    setters.put(property, setter);
                }
            }
        }
        return new ArrayList<>(setters.values());
    }

    private static List<String> constantsOf(PsiClass anEnum) {
        List<String> constants = new ArrayList<>();
        for (PsiField field : anEnum.getFields()) {
            if (field instanceof PsiEnumConstant) {
                constants.add(field.getName());
            }
        }
        return constants;
    }

    private static PsiType substitute(PsiSubstitutor substitutor, PsiType type) {
        PsiType substituted = substitutor.substitute(type);
        return substituted != null ? substituted : TypeConversionUtil.erasure(type);
    }

    /** The JVM name of a type's erasure: {@code int}, {@code a.Outer$Inner}, {@code a.B[]}. */
    static String erasedName(PsiType type) {
        PsiType erasure = TypeConversionUtil.erasure(type);
        if (erasure instanceof PsiArrayType) {
            return erasedName(((PsiArrayType) erasure).getComponentType()) + "[]";
        }
        if (erasure instanceof PsiClassType) {
            PsiClass aClass = ((PsiClassType) erasure).resolve();
            String binaryName = aClass != null ? ClassUtil.getJVMClassName(aClass) : null;
            if (binaryName != null) {
                return binaryName;
            }
        }
        return (erasure != null ? erasure : type).getCanonicalText();
    }

    private static boolean isFromJdk(@Nullable String qualifiedName) {
        return qualifiedName != null && (qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax.")
                || qualifiedName.startsWith("jdk.") || qualifiedName.startsWith("sun.")
                || qualifiedName.startsWith("com.sun."));
    }

    private static TypeDescriptor unsupported(PsiType type, String reasonKey, Object... arguments) {
        return new TypeDescriptor.UnsupportedType(type.getPresentableText(),
                MessageBundler.message(reasonKey, arguments));
    }
}
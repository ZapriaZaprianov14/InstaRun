package com.zapryan.codegen;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A {@link TypeShape} read from PSI, as seen by a test in the given package. Needs a read action. */
final class PsiTypeShape implements TypeShape {

    private static final Set<String> BOXED = Set.of(
            CommonClassNames.JAVA_LANG_BOOLEAN, CommonClassNames.JAVA_LANG_CHARACTER, CommonClassNames.JAVA_LANG_BYTE,
            CommonClassNames.JAVA_LANG_SHORT, CommonClassNames.JAVA_LANG_INTEGER, CommonClassNames.JAVA_LANG_LONG,
            CommonClassNames.JAVA_LANG_FLOAT, CommonClassNames.JAVA_LANG_DOUBLE);

    private final PsiType type;
    private final String testPackage;

    PsiTypeShape(PsiType type, String testPackage) {
        this.type = concrete(type);
        this.testPackage = testPackage;
    }

    @Override
    public Kind getKind() {
        if (type instanceof PsiPrimitiveType) {
            return Kind.PRIMITIVE;
        }
        if (type instanceof PsiArrayType) {
            return Kind.ARRAY;
        }
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (aClass == null) {
            return Kind.OTHER;
        }
        String name = aClass.getQualifiedName();
        if (name != null && BOXED.contains(name)) {
            return Kind.BOXED;
        }
        if (CommonClassNames.JAVA_LANG_STRING.equals(name)) {
            return Kind.STRING;
        }
        if ("java.math.BigDecimal".equals(name) || "java.math.BigInteger".equals(name)) {
            return Kind.BIG_NUMBER;
        }
        if (aClass.isEnum()) {
            return Kind.ENUM;
        }
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
            return Kind.MAP;
        }
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_LIST)) {
            return Kind.LIST;
        }
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_SET)) {
            return Kind.SET;
        }
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
            return Kind.COLLECTION;
        }
        return Kind.OTHER;
    }

    @Override
    public String getText() {
        return type.getCanonicalText();
    }

    @Override
    public boolean isAccessible() {
        PsiType innermost = type.getDeepComponentType();
        if (innermost instanceof PsiPrimitiveType) {
            return true;
        }
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(innermost);
        return aClass != null && Visibility.isAccessible(aClass, testPackage);
    }

    @Override
    public @Nullable TypeShape getElementType() {
        if (type instanceof PsiArrayType) {
            return new PsiTypeShape(((PsiArrayType) type).getComponentType(), testPackage);
        }
        return typeArgument(CommonClassNames.JAVA_UTIL_COLLECTION, 0);
    }

    @Override
    public @Nullable TypeShape getMapValueType() {
        return typeArgument(CommonClassNames.JAVA_UTIL_MAP, 1);
    }

    @Override
    public List<Accessor> getAccessors() {
        if (!(type instanceof PsiClassType) || !isAccessible()) {
            return List.of();
        }
        PsiClassType.ClassResolveResult resolved = ((PsiClassType) type).resolveGenerics();
        PsiClass aClass = resolved.getElement();
        if (aClass == null) {
            return List.of();
        }
        List<Accessor> accessors = new ArrayList<>();
        if (aClass.isRecord()) {
            for (PsiRecordComponent component : aClass.getRecordComponents()) {
                PsiType componentType = resolved.getSubstitutor().substitute(component.getType());
                accessors.add(new Accessor(component.getName(), new PsiTypeShape(componentType, testPackage)));
            }
            return accessors;
        }
        Map<String, Accessor> getters = new LinkedHashMap<>();
        for (PsiMethod method : aClass.getAllMethods()) {
            PsiClass declaringClass = method.getContainingClass();
            if (declaringClass == null || getters.containsKey(method.getName())
                    || CommonClassNames.JAVA_LANG_OBJECT.equals(declaringClass.getQualifiedName())
                    || method.hasModifierProperty(PsiModifier.STATIC)
                    || !PropertyUtilBase.isSimplePropertyGetter(method, true)
                    || !Visibility.isAccessible(method, testPackage)) {
                continue;
            }
            PsiSubstitutor substitutor =
                    TypeConversionUtil.getSuperClassSubstitutor(declaringClass, aClass, resolved.getSubstitutor());
            PsiType returnType = substitutor.substitute(method.getReturnType());
            if (returnType != null) {
                getters.put(method.getName(), new Accessor(method.getName(), new PsiTypeShape(returnType, testPackage)));
            }
        }
        return new ArrayList<>(getters.values());
    }

    private @Nullable TypeShape typeArgument(String superClass, int index) {
        PsiType argument = PsiUtil.substituteTypeParameter(type, superClass, index, false);
        return argument != null ? new PsiTypeShape(argument, testPackage) : null;
    }

    /** The type a value of a wildcard, captured or type variable type is known to have. */
    private static PsiType concrete(PsiType type) {
        if (type instanceof PsiCapturedWildcardType) {
            return concrete(((PsiCapturedWildcardType) type).getUpperBound());
        }
        if (type instanceof PsiWildcardType) {
            PsiWildcardType wildcard = (PsiWildcardType) type;
            return wildcard.isExtends() ? concrete(wildcard.getBound()) : PsiType.getJavaLangObject(
                    wildcard.getManager(), wildcard.getResolveScope());
        }
        if (PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter) {
            return TypeConversionUtil.erasure(type);
        }
        return type;
    }
}
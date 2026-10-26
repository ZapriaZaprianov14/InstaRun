package com.zapryan.reflection;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.TypeConversionUtil;

/** The names the JVM knows types by, which is how reflection, and the bootstrap, look classes and members up. */
public final class JvmNames {

    private JvmNames() {
    }

    /** The JVM name of a type's erasure: {@code int}, {@code a.Outer$Inner}, {@code a.B[]}. */
    public static String erasedName(PsiType type) {
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
}
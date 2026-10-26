package com.zapryan.codegen;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiUtil;

/**
 * What a test can reach from its package. A generated test lives in the package of the class it tests and extends
 * nothing, so it reaches public members of public classes, and anything not private in its own package.
 */
final class Visibility {

    private Visibility() {
    }

    static boolean isAccessible(PsiClass aClass, String packageName) {
        if (aClass instanceof PsiTypeParameter || PsiUtil.isLocalOrAnonymousClass(aClass)) {
            return false;
        }
        for (PsiClass current = aClass; current != null; current = current.getContainingClass()) {
            if (!isVisible(current, packageName)) {
                return false;
            }
        }
        return true;
    }

    static boolean isAccessible(PsiMember member, String packageName) {
        PsiClass containingClass = member.getContainingClass();
        return isVisible(member, packageName)
                && (containingClass == null || isAccessible(containingClass, packageName));
    }

    static String packageOf(PsiElement element) {
        PsiFile file = element.getContainingFile();
        return file instanceof PsiClassOwner ? ((PsiClassOwner) file).getPackageName() : "";
    }

    private static boolean isVisible(PsiMember member, String packageName) {
        if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
            return true;
        }
        if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        // Package-private, or protected, which a test that isn't a subclass only reaches from the same package.
        return packageName.equals(packageOf(member));
    }
}
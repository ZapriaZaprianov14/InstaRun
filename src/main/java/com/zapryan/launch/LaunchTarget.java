package com.zapryan.launch;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtilCore;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.MethodDescriptorFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the launch needs to know about a method, captured in a single read action.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class LaunchTarget {

    private final MethodDescriptor method;
    @Nullable
    private final Module module;
    private final boolean inTestSources;

    public static LaunchTarget of(PsiMethod method) {
        VirtualFile file = PsiUtilCore.getVirtualFile(method);
        return new LaunchTarget(
                MethodDescriptorFactory.from(method),
                ModuleUtilCore.findModuleForPsiElement(method),
                file != null && TestSourcesFilter.isTestSources(file, method.getProject()));
    }
}
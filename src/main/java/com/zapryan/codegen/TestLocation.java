package com.zapryan.codegen;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testIntegration.createTest.CreateTestAction;
import com.intellij.testIntegration.createTest.CreateTestUtils;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where the test of a class goes and which JUnit it is written for: {@code <Class>Test} in the class's package, in the
 * module IntelliJ keeps its tests in. An existing test class decides the JUnit; otherwise the test classpath does,
 * preferring JUnit 5.
 *
 * @param existingClass the test class if it exists already
 * @param testRootUrl   the test source folder to create the class in when it doesn't exist; the folder itself may
 *                      not exist yet either
 */
record TestLocation(Module testModule, String packageName, String className, @Nullable PsiClass existingClass,
                    @Nullable String testRootUrl, TestFramework framework) {

    private static final String JUNIT4_TEST = "org.junit.Test";

    /** Needs a read action. */
    static TestLocation find(Project project, Module productionModule, PsiClass classUnderTest, String methodName)
            throws CannotGenerate {
        Module testModule = CreateTestAction.suggestModuleForTests(project, productionModule);
        String packageName = Visibility.packageOf(classUnderTest);
        String className = classUnderTest.getName() + "Test";
        PsiClass existing = existingTestClass(project, testModule,
                packageName.isEmpty() ? className : packageName + "." + className);
        GlobalSearchScope testClasspath = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(testModule, true);
        TestFramework framework = existing != null ? frameworkOf(existing, project, testClasspath) : null;
        if (framework == null) {
            framework = frameworkOn(project, testClasspath);
        }
        if (framework == null) {
            throw new CannotGenerate(MessageBundler.message("generate.error.noJUnit", methodName, testModule.getName()));
        }
        String testRootUrl = null;
        if (existing == null) {
            testRootUrl = testRootUrl(testModule, packageName);
            if (testRootUrl == null) {
                throw new CannotGenerate(
                        MessageBundler.message("generate.error.noTestRoot", methodName, testModule.getName()));
            }
        }
        return new TestLocation(testModule, packageName, className, existing, testRootUrl, framework);
    }

    private static @Nullable PsiClass existingTestClass(Project project, Module testModule, String qualifiedName) {
        PsiClass found = JavaPsiFacade.getInstance(project).findClass(qualifiedName,
                GlobalSearchScope.moduleScope(testModule));
        VirtualFile file = found != null ? PsiUtilCore.getVirtualFile(found) : null;
        return file != null && TestSourcesFilter.isTestSources(file, project) ? found : null;
    }

    /** The JUnit an existing test class already uses, so a new method doesn't mix two of them. */
    private static @Nullable TestFramework frameworkOf(PsiClass testClass, Project project, GlobalSearchScope scope) {
        for (PsiMethod method : testClass.getMethods()) {
            if (AnnotationUtil.isAnnotated(method, TestFramework.JUNIT5.getTestAnnotation(), 0)) {
                return TestFramework.JUNIT5;
            }
            if (AnnotationUtil.isAnnotated(method, JUNIT4_TEST, 0)) {
                return junit4(project, scope);
            }
        }
        return null;
    }

    private static @Nullable TestFramework frameworkOn(Project project, GlobalSearchScope scope) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        if (facade.findClass(TestFramework.JUNIT5.getTestAnnotation(), scope) != null
                && facade.findClass(TestFramework.JUNIT5.getAssertionsClass(), scope) != null) {
            return TestFramework.JUNIT5;
        }
        return facade.findClass(JUNIT4_TEST, scope) != null ? junit4(project, scope) : null;
    }

    /** JUnit 4.13 added assertThrows; older versions expect exceptions through the annotation. */
    private static TestFramework junit4(Project project, GlobalSearchScope scope) {
        PsiClass assertions = JavaPsiFacade.getInstance(project).findClass(TestFramework.JUNIT4.getAssertionsClass(), scope);
        return assertions != null && assertions.findMethodsByName("assertThrows", false).length > 0
                ? TestFramework.JUNIT4
                : TestFramework.JUNIT4_WITHOUT_ASSERT_THROWS;
    }

    /**
     * An existing test source folder, preferring one that has the package already, then one named java; or else a
     * configured one that isn't created yet. Null if the module has none.
     */
    private static @Nullable String testRootUrl(Module testModule, String packageName) {
        List<VirtualFile> roots = CreateTestUtils.computeTestRoots(testModule);
        String packagePath = packageName.replace('.', '/');
        for (VirtualFile root : roots) {
            if (!packagePath.isEmpty() && root.findFileByRelativePath(packagePath) != null) {
                return root.getUrl();
            }
        }
        for (VirtualFile root : roots) {
            if (root.getName().equals("java")) {
                return root.getUrl();
            }
        }
        if (!roots.isEmpty()) {
            return roots.get(0).getUrl();
        }
        List<String> configured = CreateTestUtils.computeSuitableTestRootUrls(testModule);
        for (String url : configured) {
            if (url.endsWith("/java")) {
                return url;
            }
        }
        return configured.isEmpty() ? null : configured.get(0);
    }
}
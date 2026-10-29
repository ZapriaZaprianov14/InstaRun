package com.zapryan.codegen;

import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/** Puts a generated test method into its test class, creating the class if needed. */
final class TestFileWriter {

    private TestFileWriter() {
    }

    /** A name for the test method that the test class doesn't use yet. Needs a read action. */
    static String freeMethodName(@Nullable PsiClass testClass, String name) {
        if (testClass == null) {
            return name;
        }
        String free = name;
        for (int i = 2; testClass.findMethodsByName(free, false).length > 0; i++) {
            free = name + i;
        }
        return free;
    }

    /**
     * Adds the method, imports what it uses, drops the casts it doesn't need and formats it the way the project
     * formats code. Must run inside a write command, which makes it a single undoable step.
     */
    static PsiMethod insert(Project project, TestLocation location, GeneratedTest test) throws IOException {
        PsiClass testClass = location.existingClass() != null ? location.existingClass()
                : createTestClass(project, location);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiMethod method = (PsiMethod) testClass.add(factory.createMethodFromText(test.methodText(), testClass));
        PsiClass assertions = JavaPsiFacade.getInstance(project)
                .findClass(location.framework().getAssertionsClass(), testClass.getResolveScope());
        if (assertions != null && testClass.getContainingFile() instanceof PsiJavaFile) {
            PsiImportList imports = ((PsiJavaFile) testClass.getContainingFile()).getImportList();
            for (String member : test.staticImports()) {
                if (imports != null && !isImported(imports, assertions, member)) {
                    imports.add(factory.createImportStaticStatement(assertions, member));
                }
            }
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(method);
        removeRedundantCasts(method);
        return (PsiMethod) CodeStyleManager.getInstance(project).reformat(method);
    }

    /**
     * The test casts every null and every literal of another type than its parameter, in case the method is
     * overloaded; now that it is in its class, the IDE can tell which of those casts the calls need. One cast is
     * removed at a time, since removing one can make another one needed.
     */
    private static void removeRedundantCasts(PsiMethod method) {
        int casts = PsiTreeUtil.findChildrenOfType(method, PsiTypeCastExpression.class).size();
        for (int i = 0; i < casts; i++) {
            List<PsiTypeCastExpression> redundant = RedundantCastUtil.getRedundantCastsInside(method);
            if (redundant.isEmpty()) {
                return;
            }
            RemoveRedundantCastUtil.removeCast(redundant.get(0));
        }
    }

    private static PsiClass createTestClass(Project project, TestLocation location) throws IOException {
        String url = location.testRootUrl();
        if (url == null) {
            throw new IllegalStateException("A new test class needs a test source folder");
        }
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
        if (root == null) {
            root = VfsUtil.createDirectories(VfsUtilCore.urlToPath(url));
        }
        PsiDirectory directory = CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(
                new PackageWrapper(PsiManager.getInstance(project), location.packageName()), root);
        String fileName = location.className() + ".java";
        PsiFile existingFile = directory.findFile(fileName);
        if (existingFile instanceof PsiJavaFile && ((PsiJavaFile) existingFile).getClasses().length > 0) {
            return ((PsiJavaFile) existingFile).getClasses()[0];
        }
        String text = (location.packageName().isEmpty() ? "" : "package " + location.packageName() + ";\n\n")
                + (location.framework().needsPublicMembers() ? "public " : "")
                + "class " + location.className() + " {\n}\n";
        PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, JavaFileType.INSTANCE, text);
        return ((PsiJavaFile) directory.add(file)).getClasses()[0];
    }

    private static boolean isImported(PsiImportList imports, PsiClass assertions, String member) {
        for (PsiImportStaticStatement statement : imports.getImportStaticStatements()) {
            PsiClass target = statement.resolveTargetClass();
            if (target != null && target.isEquivalentTo(assertions)
                    && (statement.isOnDemand() || member.equals(statement.getReferenceName()))) {
                return true;
            }
        }
        return false;
    }
}
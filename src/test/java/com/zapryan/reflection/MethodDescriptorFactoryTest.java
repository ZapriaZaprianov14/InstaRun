package com.zapryan.reflection;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class MethodDescriptorFactoryTest extends BasePlatformTestCase {

    public void testTopLevelClassIsIdentifiedByItsQualifiedName() {
        MethodDescriptor descriptor = describe("package a; public class Tool { static int run() { return 1; } }", "run");

        assertEquals("a.Tool", descriptor.getDeclaringClassFqn());
        assertEquals("a.Tool", descriptor.getDeclaringClassBinaryName());
    }

    public void testNestedClassIsIdentifiedByItsBinaryName() {
        MethodDescriptor descriptor = describe(
                "package a; public class Outer { public static class Inner { static void run() {} } }", "run");

        assertEquals("a.Outer.Inner", descriptor.getDeclaringClassFqn());
        assertEquals("a.Outer$Inner", descriptor.getDeclaringClassBinaryName());
    }

    public void testLocalClassHasNoBinaryName() {
        MethodDescriptor descriptor = describe(
                "package a; public class Host { void host() { class Local { void run() {} } } }", "run");

        assertNull(descriptor.getDeclaringClassBinaryName());
    }

    public void testAnonymousClassHasNoBinaryName() {
        MethodDescriptor descriptor = describe(
                "package a; public class Host { Object host() { return new Object() { void run() {} }; } }", "run");

        assertNull(descriptor.getDeclaringClassBinaryName());
    }

    public void testConstructorIsFlagged() {
        MethodDescriptor descriptor = describe("package a; public class Tool { public Tool() {} }", "Tool");

        assertTrue(descriptor.isConstructor());
    }

    public void testStaticnessAndParametersAreCaptured() {
        MethodDescriptor descriptor = describe(
                "package a; public class Tool { static long run(int count, boolean dryRun) { return 0; } }", "run");

        assertTrue(descriptor.isStatic());
        assertFalse(descriptor.isConstructor());
        assertEquals("long", descriptor.getReturnTypeFqn());
        assertEquals(2, descriptor.getParameters().size());
        assertEquals("count", descriptor.getParameters().get(0).getName());
        assertEquals("int", descriptor.getParameters().get(0).getTypeFqn());
    }

    public void testClassWithoutDeclaredConstructorsGetsTheImplicitNoArgumentOne() {
        ReceiverDescriptor receiver = describe("package a; public class Tool { int run() { return 1; } }", "run").getReceiver();

        assertEquals(ReceiverDescriptor.Kind.INSTANTIABLE, receiver.getKind());
        assertEquals(1, receiver.getConstructors().size());
        assertTrue(receiver.getConstructors().get(0).getParameters().isEmpty());
    }

    public void testDeclaredConstructorsAreCapturedWithTheirParameters() {
        ReceiverDescriptor receiver = describe(
                "package a; public class Tool { public Tool(int size) {} private Tool(long id, boolean fast) {} int run() { return 1; } }",
                "run").getReceiver();

        assertEquals(2, receiver.getConstructors().size());
        ConstructorDescriptor second = receiver.getConstructors().get(1);
        assertEquals("id", second.getParameters().get(0).getName());
        assertEquals("boolean", second.getParameters().get(1).getTypeFqn());
    }

    public void testStaticNestedClassesCanBeCreatedButInnerClassesCannot() {
        String source = "package a; public class Outer { public static class Nested { int one() { return 1; } } "
                + "public class Inner { int two() { return 2; } } }";

        assertEquals(ReceiverDescriptor.Kind.INSTANTIABLE, describe(source, "one").getReceiver().getKind());
        assertEquals(ReceiverDescriptor.Kind.INNER_CLASS, describe(source, "two").getReceiver().getKind());
    }

    public void testAbstractClassesInterfacesAndEnumsCannotBeCreated() {
        assertEquals(ReceiverDescriptor.Kind.ABSTRACT,
                describe("package a; public abstract class Base { int run() { return 1; } }", "run").getReceiver().getKind());
        assertEquals(ReceiverDescriptor.Kind.ABSTRACT,
                describe("package a; public interface Api { default int run() { return 1; } }", "run").getReceiver().getKind());
        assertEquals(ReceiverDescriptor.Kind.ENUM,
                describe("package a; public enum Mode { ON; int run() { return 1; } }", "run").getReceiver().getKind());
    }

    private MethodDescriptor describe(String source, String methodName) {
        PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, source);
        PsiMethod method = PsiTreeUtil.findChildrenOfType(file, PsiMethod.class).stream()
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return MethodDescriptorFactory.from(method);
    }
}
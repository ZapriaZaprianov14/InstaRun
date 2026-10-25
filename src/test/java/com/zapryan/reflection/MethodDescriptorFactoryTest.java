package com.zapryan.reflection;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

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

    public void testBeanPropertiesComeFromItsConstructorsAndSetters() {
        TypeDescriptor.BeanType order = (TypeDescriptor.BeanType) describe(
                "package a; public class Shop { static void buy(Order order) {} } class Order { Order(int id) {} "
                        + "public void setCount(int count) {} private void setNote(char note) {} "
                        + "static void setGlobal(int global) {} void setup() {} }",
                "buy").getParameters().get(0).getType();

        assertEquals("a.Order", order.getBinaryName());
        assertEquals(List.of("id", "count", "note"), namesOf(order.getProperties()));
    }

    public void testInheritedSettersSeeTheSubclassTypeArguments() {
        TypeDescriptor.BeanType box = (TypeDescriptor.BeanType) describe(
                "package a; public class Shop { static void buy(Box box) {} } "
                        + "class Holder<T> { public void setValue(T value) {} } class Box extends Holder<Item> {} "
                        + "class Item { public void setWeight(int weight) {} }",
                "buy").getParameters().get(0).getType();

        SetterDescriptor value = box.findSetter("value");
        assertEquals("a.Holder", value.getDeclaringClassBinaryName());
        assertEquals("java.lang.Object", value.getErasedParameterTypeName());
        assertEquals("a.Item", ((TypeDescriptor.BeanType) value.getType()).getBinaryName());
    }

    public void testASelfReferencingBeanIsDescribedOnce() {
        TypeDescriptor.BeanType node = (TypeDescriptor.BeanType) describe(
                "package a; public class Tree { static void walk(Node node) {} } "
                        + "class Node { public void setNext(Node next) {} }",
                "walk").getParameters().get(0).getType();

        assertSame(node, node.findSetter("next").getType());
    }

    public void testCollectionsArraysAndEnumsAreDescribed() {
        addJdkStubs();
        List<ParameterDescriptor> parameters = describe(
                "package a; import java.util.List; import java.util.Map; public class Shop { "
                        + "static void buy(List<Item> items, Size[] sizes, Map<String, Item> byName) {} } "
                        + "enum Size { S, M, L } class Item {}",
                "buy").getParameters();

        TypeDescriptor.CollectionType items = (TypeDescriptor.CollectionType) parameters.get(0).getType();
        assertEquals("java.util.ArrayList", items.getImplementationClassName());
        assertEquals("a.Item", ((TypeDescriptor.BeanType) items.getElementType()).getBinaryName());
        assertEquals("java.util.List", parameters.get(0).getErasedTypeName());

        TypeDescriptor.ArrayType sizes = (TypeDescriptor.ArrayType) parameters.get(1).getType();
        assertEquals(List.of("S", "M", "L"), ((TypeDescriptor.EnumType) sizes.getElementType()).getConstants());
        assertEquals("a.Size[]", parameters.get(1).getErasedTypeName());

        assertFalse(parameters.get(2).isSupported());
    }

    public void testTypesWithoutAClassToBuildAreUnsupported() {
        List<ParameterDescriptor> parameters = describe(
                "package a; public class Shop { <T> void buy(Api api, Inner inner, T anything) {} class Inner {} } "
                        + "interface Api {}",
                "buy").getParameters();

        assertFalse(parameters.get(0).isSupported());
        assertFalse(parameters.get(1).isSupported());
        assertFalse(parameters.get(2).isSupported());
    }

    public void testParameterTypesAreErasedToBinaryNames() {
        ParameterDescriptor parameter = describe(
                "package a; public class Outer { static void run(Inner[] values) {} static class Inner {} }",
                "run").getParameters().get(0);

        assertEquals("a.Outer$Inner[]", parameter.getErasedTypeName());
        assertEquals("a.Outer.Inner[]", parameter.getTypeFqn());
    }

    public void testClassesWithASpringStereotypeAreSpringBeans() {
        addSpringStubs();

        ReceiverDescriptor component = describe(
                "package a; import org.springframework.stereotype.Component; @Component public class Tool { int run() { return 1; } }",
                "run").getReceiver();
        assertEquals(ReceiverDescriptor.Kind.SPRING_BEAN, component.getKind());
        assertEquals("@Component", component.getSpringEvidence());
        assertTrue(component.getConstructors().isEmpty());

        ReceiverDescriptor service = describe(
                "package a; import org.springframework.stereotype.Service; @Service public class Orders { int run() { return 1; } }",
                "run").getReceiver();
        assertEquals(ReceiverDescriptor.Kind.SPRING_BEAN, service.getKind());
        assertEquals("@Service", service.getSpringEvidence());
    }

    public void testClassesWithInjectedDependenciesAreSpringBeansEvenWhenInherited() {
        addSpringStubs();

        ReceiverDescriptor receiver = describe(
                "package a; import org.springframework.beans.factory.annotation.Autowired; "
                        + "public class Orders extends Base { int run() { return 1; } } "
                        + "class Base { @Autowired Repository repository; } class Repository {}",
                "run").getReceiver();

        assertEquals(ReceiverDescriptor.Kind.SPRING_BEAN, receiver.getKind());
        assertEquals("@Autowired", receiver.getSpringEvidence());
    }

    public void testPlainAndAbstractClassesAreNotSpringBeans() {
        addSpringStubs();

        ReceiverDescriptor plain = describe("package a; public class Tool { int run() { return 1; } }", "run").getReceiver();
        assertEquals(ReceiverDescriptor.Kind.INSTANTIABLE, plain.getKind());
        assertNull(plain.getSpringEvidence());

        assertEquals(ReceiverDescriptor.Kind.ABSTRACT, describe(
                "package a; import org.springframework.stereotype.Service; @Service public abstract class Base { int run() { return 1; } }",
                "run").getReceiver().getKind());
    }

    /** The light fixture has no JDK, so the few JDK types these tests need are declared as project sources. */
    private void addJdkStubs() {
        myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}");
        myFixture.addFileToProject("java/util/Collection.java", "package java.util; public interface Collection<E> {}");
        myFixture.addFileToProject("java/util/List.java",
                "package java.util; public interface List<E> extends Collection<E> {}");
        myFixture.addFileToProject("java/util/Map.java", "package java.util; public interface Map<K, V> {}");
    }

    /** The Spring annotations the guard looks for, declared as project sources since the fixture has no Spring. */
    private void addSpringStubs() {
        myFixture.addFileToProject("org/springframework/stereotype/Component.java",
                "package org.springframework.stereotype; public @interface Component {}");
        myFixture.addFileToProject("org/springframework/stereotype/Service.java",
                "package org.springframework.stereotype; @Component public @interface Service {}");
        myFixture.addFileToProject("org/springframework/beans/factory/annotation/Autowired.java",
                "package org.springframework.beans.factory.annotation; public @interface Autowired {}");
    }

    private static List<String> namesOf(List<PropertyDescriptor> properties) {
        return properties.stream().map(PropertyDescriptor::getName).toList();
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
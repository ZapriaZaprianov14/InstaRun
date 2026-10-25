package com.zapryan.reflection;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RunBlockerTest {

    private static final ParameterDescriptor COUNT = literal("count", "int");
    private static final ParameterDescriptor NAME = literal("name", "java.lang.String");
    private static final ParameterDescriptor ITEMS = new ParameterDescriptor("items",
            "java.util.Map<java.lang.String,java.lang.Object>", "java.util.Map",
            new TypeDescriptor.UnsupportedType("Map<String, Object>", "maps are not supported"));
    private static final ReceiverDescriptor NO_ARGUMENT_CONSTRUCTOR = new ReceiverDescriptor(
            ReceiverDescriptor.Kind.INSTANTIABLE, List.of(new ConstructorDescriptor(List.of())));

    @Test
    public void staticMethodWithSupportedParametersHasNoBlockers() {
        assertEquals(List.of(), RunBlocker.findAll(staticMethod(COUNT, NAME)));
    }

    @Test
    public void instanceMethodWithAUsableConstructorHasNoBlockers() {
        assertEquals(List.of(), RunBlocker.findAll(instanceMethod(NO_ARGUMENT_CONSTRUCTOR, COUNT)));
    }

    @Test
    public void unsupportedParameterTypeIsBlocked() {
        assertEquals(List.of(RunBlocker.UNSUPPORTED_PARAMETER_TYPE), RunBlocker.findAll(staticMethod(COUNT, ITEMS)));
    }

    @Test
    public void receiverThatCannotBeCreatedIsBlocked() {
        assertEquals(List.of(RunBlocker.ABSTRACT_RECEIVER),
                RunBlocker.findAll(instanceMethod(cannotBeCreated(ReceiverDescriptor.Kind.ABSTRACT))));
        assertEquals(List.of(RunBlocker.ENUM_RECEIVER),
                RunBlocker.findAll(instanceMethod(cannotBeCreated(ReceiverDescriptor.Kind.ENUM))));
        assertEquals(List.of(RunBlocker.INNER_CLASS_RECEIVER),
                RunBlocker.findAll(instanceMethod(cannotBeCreated(ReceiverDescriptor.Kind.INNER_CLASS))));
    }

    @Test
    public void classWithoutAUsableConstructorIsBlocked() {
        ReceiverDescriptor onlyUnsupportedConstructor = new ReceiverDescriptor(
                ReceiverDescriptor.Kind.INSTANTIABLE, List.of(new ConstructorDescriptor(List.of(ITEMS))));

        assertEquals(List.of(RunBlocker.NO_USABLE_CONSTRUCTOR),
                RunBlocker.findAll(instanceMethod(onlyUnsupportedConstructor)));
    }

    @Test
    public void receiverDoesNotMatterForStaticMethods() {
        MethodDescriptor staticMethodOfAbstractClass =
                method("com.acme.Tool", true, false, cannotBeCreated(ReceiverDescriptor.Kind.ABSTRACT));

        assertEquals(List.of(), RunBlocker.findAll(staticMethodOfAbstractClass));
    }

    @Test
    public void methodOfLocalOrAnonymousClassIsBlocked() {
        assertEquals(List.of(RunBlocker.LOCAL_OR_ANONYMOUS_CLASS),
                RunBlocker.findAll(method(null, false, false, NO_ARGUMENT_CONSTRUCTOR)));
    }

    @Test
    public void everyApplicableBlockerIsReported() {
        assertEquals(List.of(RunBlocker.ENUM_RECEIVER, RunBlocker.UNSUPPORTED_PARAMETER_TYPE),
                RunBlocker.findAll(instanceMethod(cannotBeCreated(ReceiverDescriptor.Kind.ENUM), ITEMS)));
    }

    @Test
    public void constructorIsReportedOnItsOwn() {
        assertEquals(List.of(RunBlocker.CONSTRUCTOR),
                RunBlocker.findAll(method("com.acme.Tool", false, true, NO_ARGUMENT_CONSTRUCTOR, ITEMS)));
    }

    @Test
    public void settersAreBlockedRegardlessOfReturnType() {
        assertEquals(List.of(RunBlocker.GETTER_OR_SETTER),
                RunBlocker.findAll(namedInstanceMethod("setName", "void", NAME)));
        assertEquals(List.of(RunBlocker.GETTER_OR_SETTER),
                RunBlocker.findAll(namedInstanceMethod("setName", "com.acme.Tool", NAME)));
    }

    @Test
    public void gettersAreBlockedButNotAVoidOrMisnamedOne() {
        assertEquals(List.of(RunBlocker.GETTER_OR_SETTER),
                RunBlocker.findAll(namedInstanceMethod("getName", "java.lang.String")));
        assertEquals(List.of(), RunBlocker.findAll(namedInstanceMethod("getName", "void")));
        assertEquals(List.of(), RunBlocker.findAll(namedInstanceMethod("get", "java.lang.String")));
    }

    @Test
    public void booleanIsGettersAreBlockedButNonBooleanOnesAreNot() {
        assertEquals(List.of(RunBlocker.GETTER_OR_SETTER),
                RunBlocker.findAll(namedInstanceMethod("isEmpty", "boolean")));
        assertEquals(List.of(), RunBlocker.findAll(namedInstanceMethod("isEmpty", "java.lang.String")));
    }

    @Test
    public void staticMethodsAreNeverTreatedAsGettersOrSetters() {
        assertEquals(List.of(), RunBlocker.findAll(
                method("com.acme.Tool", true, false, "getInstance", "com.acme.Tool", NO_ARGUMENT_CONSTRUCTOR)));
    }

    @Test
    public void aFluentSetterIsStillBlockedEvenIfCombinedWithOtherBlockers() {
        assertEquals(List.of(RunBlocker.ENUM_RECEIVER, RunBlocker.GETTER_OR_SETTER),
                RunBlocker.findAll(method("com.acme.Tool", false, false, "setName",
                        "com.acme.Tool", cannotBeCreated(ReceiverDescriptor.Kind.ENUM), NAME)));
    }

    private static MethodDescriptor staticMethod(ParameterDescriptor... parameters) {
        return method("com.acme.Tool", true, false, NO_ARGUMENT_CONSTRUCTOR, parameters);
    }

    private static MethodDescriptor instanceMethod(ReceiverDescriptor receiver, ParameterDescriptor... parameters) {
        return method("com.acme.Tool", false, false, receiver, parameters);
    }

    private static MethodDescriptor namedInstanceMethod(String methodName, String returnTypeFqn,
                                                        ParameterDescriptor... parameters) {
        return method("com.acme.Tool", false, false, methodName, returnTypeFqn, NO_ARGUMENT_CONSTRUCTOR, parameters);
    }

    private static MethodDescriptor method(String binaryName, boolean isStatic, boolean isConstructor,
                                           ReceiverDescriptor receiver, ParameterDescriptor... parameters) {
        return method(binaryName, isStatic, isConstructor, "run", "void", receiver, parameters);
    }

    private static MethodDescriptor method(String binaryName, boolean isStatic, boolean isConstructor,
                                           String methodName, String returnTypeFqn, ReceiverDescriptor receiver,
                                           ParameterDescriptor... parameters) {
        return new MethodDescriptor("com.acme.Tool", binaryName, methodName, returnTypeFqn, isStatic, isConstructor,
                List.of(parameters), receiver);
    }

    private static ReceiverDescriptor cannotBeCreated(ReceiverDescriptor.Kind kind) {
        return new ReceiverDescriptor(kind, List.of());
    }

    private static ParameterDescriptor literal(String name, String type) {
        return new ParameterDescriptor(name, type, type, new TypeDescriptor.LiteralType(type, type));
    }
}
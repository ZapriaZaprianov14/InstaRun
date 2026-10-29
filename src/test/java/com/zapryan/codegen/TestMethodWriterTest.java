package com.zapryan.codegen;

import com.zapryan.bootstrap.Observation;
import com.zapryan.bootstrap.Outcome;
import com.zapryan.launch.MethodInput;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ReceiverDescriptor;
import com.zapryan.reflection.ValueSpec;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.zapryan.reflection.TestTypes.INT;
import static com.zapryan.reflection.TestTypes.constructor;
import static com.zapryan.reflection.TestTypes.parameter;
import static org.junit.Assert.assertEquals;

public class TestMethodWriterTest {

    private static final MethodDescriptor ADD = new MethodDescriptor("com.acme.Calculator", "com.acme.Calculator", "add",
            "int", false, false, List.of(parameter("a", INT), parameter("b", INT)),
            new ReceiverDescriptor(ReceiverDescriptor.Kind.INSTANTIABLE, List.of(constructor())));
    private static final MethodInput TWO_AND_THREE = new MethodInput(
            new ValueSpec.Constructed("com.acme.Calculator", List.of(), List.of()),
            List.of(new ValueSpec.Literal(2), new ValueSpec.Literal(3)));

    private final FakeContext context = new FakeContext();

    @Test
    public void writesAJUnit5TestThatChecksTheReturnedValue() {
        GeneratedTest test = TestMethodWriter.write(subject(ADD, "int", FakeShape.primitive("int")), TWO_AND_THREE,
                new Outcome.Returned(new Observation.Literal(5)), TestFramework.JUNIT5, context);

        assertEquals("""
                @org.junit.jupiter.api.Test
                void add() {
                    com.acme.Calculator calculator = new com.acme.Calculator();

                    int result = calculator.add(2, 3);

                    // Recorded
                    assertEquals(5, result);
                }
                """, test.methodText());
        assertEquals(Set.of("assertEquals"), test.staticImports());
    }

    @Test
    public void writesPublicJUnit4TestsAndDeclaresCheckedExceptions() {
        TestSubject subject = new TestSubject(ADD, "add", "com.acme.Calculator", "int", FakeShape.primitive("int"), true);

        GeneratedTest test = TestMethodWriter.write(subject, TWO_AND_THREE, new Outcome.Returned(new Observation.Literal(5)),
                TestFramework.JUNIT4, context);

        assertEquals("""
                @org.junit.Test
                public void add() throws java.lang.Exception {
                    com.acme.Calculator calculator = new com.acme.Calculator();

                    int result = calculator.add(2, 3);

                    // Recorded
                    assertEquals(5, result);
                }
                """, test.methodText());
    }

    @Test
    public void checksTheExceptionAndItsMessageWithAssertThrows() {
        GeneratedTest test = TestMethodWriter.write(subject(ADD, "int", FakeShape.primitive("int")), TWO_AND_THREE,
                new Outcome.Threw(List.of("java.lang.ArithmeticException", "java.lang.RuntimeException"), "overflow"),
                TestFramework.JUNIT5, context);

        assertEquals("""
                @org.junit.jupiter.api.Test
                void add() {
                    com.acme.Calculator calculator = new com.acme.Calculator();

                    java.lang.ArithmeticException exception = assertThrows(java.lang.ArithmeticException.class, () -> calculator.add(2, 3));

                    // Recorded
                    assertEquals("overflow", exception.getMessage());
                }
                """, test.methodText());
        assertEquals(Set.of("assertEquals", "assertThrows"), test.staticImports());
    }

    @Test
    public void expectsAnExceptionTheTestCantNameByItsNearestNameableSuperclass() {
        context.hide("com.acme.Calculator$Overflow");

        GeneratedTest test = TestMethodWriter.write(subject(ADD, "int", FakeShape.primitive("int")), TWO_AND_THREE,
                new Outcome.Threw(List.of("com.acme.Calculator$Overflow", "java.lang.RuntimeException"), null),
                TestFramework.JUNIT4_WITHOUT_ASSERT_THROWS, context);

        assertEquals("""
                @org.junit.Test(expected = java.lang.RuntimeException.class)
                public void add() {
                    com.acme.Calculator calculator = new com.acme.Calculator();

                    calculator.add(2, 3);
                }
                """, test.methodText());
        assertEquals(Set.of(), test.staticImports());
    }

    @Test
    public void callsStaticMethodsOnTheirClassAndSaysWhenNothingWasReturned() {
        MethodDescriptor reset = new MethodDescriptor("com.acme.Registry", "com.acme.Registry", "reset", "void", true,
                false, List.of(), new ReceiverDescriptor(ReceiverDescriptor.Kind.INSTANTIABLE, List.of(constructor())));

        GeneratedTest test = TestMethodWriter.write(new TestSubject(reset, "reset2", null, null, null, false),
                new MethodInput(null, List.of()), new Outcome.ReturnedVoid(), TestFramework.JUNIT5, context);

        assertEquals("""
                @org.junit.jupiter.api.Test
                void reset2() {
                    com.acme.Registry.reset();

                    // Returned nothing
                }
                """, test.methodText());
    }

    private static TestSubject subject(MethodDescriptor method, String resultType, TypeShape resultShape) {
        return new TestSubject(method, method.getMethodName(), method.getDeclaringClassFqn(), resultType, resultShape,
                false);
    }
}
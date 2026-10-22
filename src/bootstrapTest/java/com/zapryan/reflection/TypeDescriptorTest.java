package com.zapryan.reflection;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zapryan.reflection.TestTypes.INT;
import static com.zapryan.reflection.TestTypes.INTEGER;
import static com.zapryan.reflection.TestTypes.STRING;
import static com.zapryan.reflection.TestTypes.arrayOf;
import static com.zapryan.reflection.TestTypes.bean;
import static com.zapryan.reflection.TestTypes.constructor;
import static com.zapryan.reflection.TestTypes.listOf;
import static com.zapryan.reflection.TestTypes.parameter;
import static com.zapryan.reflection.TestTypes.setter;
import static com.zapryan.reflection.TestTypes.unsupported;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TypeDescriptorTest {

    @Test
    public void aBeanWhoseEveryConstructorNeedsSomethingUnsupportedIsUnsupportedAndSoIsWhatNeedsIt() {
        TypeDescriptor.BeanType outer = bean("Outer");
        TypeDescriptor.BeanType inner = bean("Inner");
        outer.define(Collections.singletonList(constructor(parameter("inner", inner))), Collections.emptyList());
        inner.define(Collections.singletonList(constructor(parameter("meta", unsupported("Map<String, Object>")))),
                Collections.emptyList());

        TypeDescriptor.settle(Arrays.asList(outer, inner));

        assertFalse(inner.isSupported());
        assertFalse(outer.isSupported());
        assertSame(outer, outer.getUnsupportedCause());
        assertSame(inner, listOf(inner).getUnsupportedCause());
    }

    @Test
    public void beansThatNeedEachOtherAreSupportedBecauseNullEndsTheCycle() {
        TypeDescriptor.BeanType chicken = bean("Chicken");
        TypeDescriptor.BeanType egg = bean("Egg");
        chicken.define(Collections.singletonList(constructor(parameter("egg", egg))), Collections.emptyList());
        egg.define(Collections.singletonList(constructor(parameter("chicken", chicken))), Collections.emptyList());

        TypeDescriptor.settle(Arrays.asList(chicken, egg));

        assertTrue(chicken.isSupported());
        assertTrue(egg.isSupported());
    }

    @Test
    public void propertiesListTheWidestUsableConstructorFirstThenSetters() {
        TypeDescriptor.BeanType person = bean("Person");
        person.define(
                Arrays.asList(constructor(parameter("name", STRING)),
                        constructor(parameter("meta", unsupported("Object")), parameter("id", INT), parameter("tag", STRING)),
                        constructor(parameter("age", INT), parameter("name", STRING))),
                Arrays.asList(setter(person, "email", STRING), setter(person, "name", STRING),
                        setter(person, "scores", arrayOf(INTEGER))));
        TypeDescriptor.settle(Collections.singletonList(person));

        List<String> names = new ArrayList<>();
        for (PropertyDescriptor property : person.getProperties()) {
            names.add(property.getName());
        }

        assertEquals(Arrays.asList("age", "name", "email", "scores"), names);
    }

    @Test
    public void findsSettersByPropertyName() {
        TypeDescriptor.BeanType person = bean("Person");
        SetterDescriptor email = setter(person, "email", STRING);
        person.define(Collections.singletonList(constructor()), Collections.singletonList(email));

        assertSame(email, person.findSetter("email"));
        assertNull(person.findSetter("name"));
    }

    @Test
    public void setterNamesFollowTheJavaBeansRules() {
        assertEquals("firstName", SetterDescriptor.propertyNameOf("setFirstName"));
        assertEquals("x", SetterDescriptor.propertyNameOf("setX"));
        assertEquals("URL", SetterDescriptor.propertyNameOf("setURL"));
        assertNull(SetterDescriptor.propertyNameOf("set"));
        assertNull(SetterDescriptor.propertyNameOf("settle"));
        assertNull(SetterDescriptor.propertyNameOf("reset"));
    }

    @Test
    public void declaredCollectionTypesGetAnImplementationThatKeepsTheEnteredOrder() {
        assertEquals("java.util.ArrayList", TypeDescriptor.CollectionType.implementationOf("java.util.List"));
        assertEquals("java.util.LinkedHashSet", TypeDescriptor.CollectionType.implementationOf("java.util.Set"));
        assertEquals("java.util.TreeSet", TypeDescriptor.CollectionType.implementationOf("java.util.SortedSet"));
        assertEquals("java.util.LinkedList", TypeDescriptor.CollectionType.implementationOf("java.util.Queue"));
        assertNull(TypeDescriptor.CollectionType.implementationOf("java.util.Map"));
    }

    @Test
    public void onlyPrimitivesCannotBeNull() {
        assertFalse(INT.isNullable());
        assertTrue(INTEGER.isNullable());
        assertTrue(listOf(INT).isNullable());
        assertTrue(bean("Anything").isNullable());
    }
}
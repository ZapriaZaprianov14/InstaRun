package com.zapryan.codegen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VariableNamesTest {

    @Test
    public void numbersRepeatedNames() {
        VariableNames names = new VariableNames();

        assertEquals("person", names.allocate("person"));
        assertEquals("person2", names.allocate("person"));
        assertEquals("person3", names.allocate("person"));
    }

    @Test
    public void neverUsesKeywordsOrInvalidNames() {
        VariableNames names = new VariableNames();

        assertEquals("classValue", names.allocate("class"));
        assertEquals("value", names.allocate("-"));
        assertEquals("fullname", names.allocate("full-name"));
    }

    @Test
    public void namesVariablesAfterTheirClass() {
        assertEquals("orderService", VariableNames.forClass("OrderService"));
        assertEquals("urlParser", VariableNames.forClass("URLParser"));
        assertEquals("io", VariableNames.forClass("IO"));
        assertEquals("tool", VariableNames.forClass("tool"));
    }
}
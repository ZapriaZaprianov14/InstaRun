package com.instarun.launch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShownResultListenerTest {

    @Test
    public void shortensTheResultTypeForThePopupTitle() {
        assertEquals("Order", ShownResultListener.shortTypeText("shop.Order"));
        assertEquals("List<Item>", ShownResultListener.shortTypeText("java.util.List<shop.Item>"));
        assertEquals("Map.Entry<String, int[]>",
                ShownResultListener.shortTypeText("java.util.Map.Entry<java.lang.String, int[]>"));
        assertEquals("OrderService.Circle", ShownResultListener.shortTypeText("shop.OrderService.Circle"));
        assertEquals("OrderService$1", ShownResultListener.shortTypeText("shop.OrderService$1"));
        assertEquals("int", ShownResultListener.shortTypeText("int"));
    }
}
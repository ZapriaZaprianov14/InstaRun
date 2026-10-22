package com.zapryan.bootstrap;

import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.ValueSpec;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InvocationRequestCodecTest {

    @Test
    public void decodesWhatItEncodes() throws Exception {
        ValueSpec receiver = new ValueSpec.Constructed(
                "com.acme.Outer$Inner", Collections.singletonList("long"),
                Collections.<ValueSpec>singletonList(new ValueSpec.Literal(5L)));
        InvocationRequest request = new InvocationRequest("com.acme.Outer$Inner", "compute",
                Arrays.asList("int", "java.lang.String", "java.lang.Character"), receiver,
                Arrays.asList(new ValueSpec.Literal(3), new ValueSpec.Literal("text"), new ValueSpec.Null()));

        assertEquals(request, InvocationRequestCodec.decode(InvocationRequestCodec.encode(request)));
    }

    @Test
    public void decodesBeansCollectionsArraysAndEnums() throws Exception {
        ValueSpec order = new ValueSpec.Constructed("com.acme.Order", Collections.singletonList("java.lang.String"),
                Collections.<ValueSpec>singletonList(new ValueSpec.Literal("A-1")),
                Collections.singletonList(new SetterCall("com.acme.Order", "setItems", "java.util.List",
                        new ValueSpec.CollectionOf("java.util.ArrayList",
                                Collections.<ValueSpec>singletonList(new ValueSpec.EnumConstant("com.acme.Item", "BOOK"))))));
        ValueSpec amounts = new ValueSpec.ArrayOf("java.math.BigDecimal",
                Collections.<ValueSpec>singletonList(new ValueSpec.Literal(new BigDecimal("1.50"))));
        InvocationRequest request = new InvocationRequest("com.acme.Shop", "checkout",
                Arrays.asList("com.acme.Order", "java.math.BigDecimal[]"), null, Arrays.asList(order, amounts));

        assertEquals(request, InvocationRequestCodec.decode(InvocationRequestCodec.encode(request)));
    }

    @Test
    public void encodesToASingleCommandLineToken() {
        InvocationRequest request = new InvocationRequest("com.acme.Tool", "run",
                Collections.<String>emptyList(), null, Collections.<ValueSpec>emptyList());

        String encoded = InvocationRequestCodec.encode(request);

        assertTrue(encoded, encoded.matches("[A-Za-z0-9+/=]+"));
    }
}
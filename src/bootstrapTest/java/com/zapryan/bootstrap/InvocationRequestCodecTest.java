package com.zapryan.bootstrap;

import com.zapryan.reflection.ValueSpec;
import org.junit.Test;

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
    public void encodesToASingleCommandLineToken() {
        InvocationRequest request = new InvocationRequest("com.acme.Tool", "run",
                Collections.<String>emptyList(), null, Collections.<ValueSpec>emptyList());

        String encoded = InvocationRequestCodec.encode(request);

        assertTrue(encoded, encoded.matches("[A-Za-z0-9+/=]+"));
    }
}
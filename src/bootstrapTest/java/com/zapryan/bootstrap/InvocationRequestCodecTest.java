package com.zapryan.bootstrap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InvocationRequestCodecTest {

    @Test
    public void decodesWhatItEncodes() throws Exception {
        InvocationRequest request = new InvocationRequest("com.acme.Outer$Inner", "compute");

        assertEquals(request, InvocationRequestCodec.decode(InvocationRequestCodec.encode(request)));
    }

    @Test
    public void encodesToASingleCommandLineToken() {
        String encoded = InvocationRequestCodec.encode(new InvocationRequest("com.acme.Tool", "run"));

        assertTrue(encoded, encoded.matches("[A-Za-z0-9+/=]+"));
    }
}
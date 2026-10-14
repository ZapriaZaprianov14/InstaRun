package com.zapryan.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * Turns an {@link InvocationRequest} into a single command-line-safe token and back.
 */
public final class InvocationRequestCodec {

    private InvocationRequestCodec() {
    }

    public static String encode(InvocationRequest request) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(request);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize " + request, e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static InvocationRequest decode(String encoded) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (InvocationRequest) in.readObject();
        }
    }
}
package com.zapryan.launch;

import com.intellij.openapi.diagnostic.Logger;
import com.zapryan.bootstrap.Outcome;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/** Reads the {@link Outcome} a run recorded for test generation. */
final class OutcomeReader {

    private static final Logger LOG = Logger.getInstance(OutcomeReader.class);

    /**
     * Only what an Outcome is made of may be read back; the file was written by a process running the user's code.
     * A map reading itself back also checks the Map.Entry array it allocates.
     */
    private static final ObjectInputFilter ONLY_OUTCOMES = ObjectInputFilter.Config.createFilter(
            "com.zapryan.bootstrap.*;java.lang.*;java.math.*;java.util.ArrayList;java.util.HashMap;"
                    + "java.util.LinkedHashMap;java.util.Map$Entry;!*");

    private OutcomeReader() {
    }

    /** Null when the run ended without recording one, e.g. it was stopped, or when the file can't be read. */
    static @Nullable Outcome read(File file) {
        if (file.length() == 0) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            in.setObjectInputFilter(ONLY_OUTCOMES);
            Object outcome = in.readObject();
            return outcome instanceof Outcome ? (Outcome) outcome : null;
        } catch (IOException | ClassNotFoundException e) {
            LOG.warn("Could not read the outcome the run recorded in " + file, e);
            return null;
        }
    }
}
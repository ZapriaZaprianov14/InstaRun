package com.zapryan.codegen;

/** Why no test can be generated, in words for the user. */
final class CannotGenerate extends Exception {

    CannotGenerate(String message) {
        super(message);
    }
}
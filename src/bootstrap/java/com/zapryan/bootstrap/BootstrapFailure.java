package com.zapryan.bootstrap;

/**
 * The requested call could not be set up; as opposed to an exception thrown by the user's own code.
 */
final class BootstrapFailure extends Exception {

    BootstrapFailure(String message) {
        super(message);
    }
}
package com.zapryan.bootstrap.fixtures;

public class TargetMethods {

    public static int answer() {
        return 42;
    }

    public static Object nothing() {
        return null;
    }

    public static void noResult() {
    }

    public static void fail() {
        throw new IllegalStateException("boom");
    }

    private static String hidden() {
        return "hidden";
    }

    public int instanceMethod() {
        return 7;
    }

    public static class Nested {
        public static String name() {
            return "nested";
        }
    }
}
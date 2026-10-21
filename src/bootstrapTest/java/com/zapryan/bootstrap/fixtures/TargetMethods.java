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

    public static String repeat(String text, int times) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < times; i++) {
            result.append(text);
        }
        return result.toString();
    }

    public static String primitives(boolean z, byte b, char c, short s, int i, long l, float f, double d) {
        return z + " " + b + " " + c + " " + s + " " + i + " " + l + " " + f + " " + d;
    }

    public static String describe(Integer value) {
        return value == null ? "no value" : "value " + value;
    }

    public static class Nested {
        public static String name() {
            return "nested";
        }
    }

    public static class Greeter {
        private final String greeting;

        public Greeter() {
            this("Hello");
        }

        private Greeter(String greeting) {
            this.greeting = greeting;
        }

        public String greet(String name) {
            return greeting + ", " + name;
        }
    }

    public static class Exploding {
        public Exploding() {
            throw new IllegalStateException("constructor boom");
        }

        public void run() {
        }
    }
}
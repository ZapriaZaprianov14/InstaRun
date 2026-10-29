package com.zapryan.bootstrap.fixtures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Values for the observer to record, shaped like what real methods return. */
public class ResultTargets {

    public static Order order() {
        return new Order();
    }

    public static Shape shape() {
        return new Circle();
    }

    public static Node loop() {
        Node node = new Node();
        node.next = node;
        return node;
    }

    public static Level deep() {
        return new Level(new Level(new Level(new Level(null))));
    }

    public static List<Integer> numbers(int count) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    public static List<List<List<Integer>>> cube(int size) {
        List<List<List<Integer>>> cube = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            List<List<Integer>> plane = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                plane.add(numbers(size));
            }
            cube.add(plane);
        }
        return cube;
    }

    public static Box<Customer> boxed() {
        return new Box<>(new Customer());
    }

    public static List<Shape> shapes() {
        return Collections.<Shape>singletonList(new Circle());
    }

    public static List<Circle> circles() {
        return Collections.singletonList(new Circle());
    }

    public static Object secret() {
        return Secret.HIDDEN;
    }

    public static <T> T anything() {
        return null;
    }

    public static void refuse() {
        throw new Refused();
    }

    public enum Status {
        OPEN,
        CLOSED {
            @Override
            public String toString() {
                return "closed";
            }
        }
    }

    public static class Order {
        public long getId() {
            return 7L;
        }

        public boolean isPaid() {
            return true;
        }

        public Boolean isGift() {
            return Boolean.FALSE;
        }

        public String getNote() {
            return null;
        }

        public BigDecimal getTotal() {
            return new BigDecimal("19.90");
        }

        public Status getStatus() {
            return Status.CLOSED;
        }

        public Customer getCustomer() {
            return new Customer();
        }

        public List<String> getLines() {
            return Arrays.asList("pen", "ink");
        }

        public Set<Integer> getCodes() {
            return new LinkedHashSet<>(Arrays.asList(3, 1));
        }

        public int[] getScores() {
            return new int[]{4, 2};
        }

        public Map<String, Integer> getStock() {
            Map<String, Integer> stock = new LinkedHashMap<>();
            stock.put("pen", 2);
            return stock;
        }

        public LocalDate getPlaced() {
            return LocalDate.of(2024, 1, 2);
        }

        public Object getLock() {
            return new Object();
        }

        public int getBroken() {
            throw new IllegalStateException("not loaded");
        }

        public String compute() {
            return "not a getter";
        }

        public String getWithArgument(int index) {
            return "not a getter";
        }

        public static String getStatic() {
            return "not a getter";
        }
    }

    public static class Customer {
        public String getName() {
            return "Ann";
        }
    }

    public interface Shape {
        double getArea();
    }

    private static class Circle implements Shape {
        @Override
        public double getArea() {
            return 3.5;
        }

        public double getRadius() {
            return 1.0;
        }
    }

    public static class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }

    public static class Names extends ArrayList<String> {
    }

    private static class Refused extends IllegalArgumentException {
    }

    private enum Secret {
        HIDDEN
    }

    public static class Node {
        Node next;

        public Node getNext() {
            return next;
        }
    }

    public static class Level {
        private final Level inner;

        Level(Level inner) {
            this.inner = inner;
        }

        public Level getInner() {
            return inner;
        }

        public List<String> getEmpty() {
            return Collections.emptyList();
        }
    }
}
package com.zapryan.bootstrap.fixtures;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public class BeanTargets {

    public static String describe(Person person) {
        return person.toString();
    }

    public static String describeTeam(Team team) {
        return team.members + " " + team.tags.getClass().getSimpleName() + team.tags + " " + team.status;
    }

    public static String describeCollection(Collection<?> values) {
        return values.getClass().getSimpleName() + values;
    }

    public static String sum(int[] numbers) {
        int sum = 0;
        for (int number : numbers) {
            sum += number;
        }
        return "sum " + sum;
    }

    public static String grid(String[][] rows) {
        StringBuilder result = new StringBuilder();
        for (String[] row : rows) {
            result.append(String.join(",", row)).append(';');
        }
        return result.toString();
    }

    public static String price(BigDecimal amount) {
        return amount.toPlainString() + " scale " + amount.scale();
    }

    public static String status(Status status) {
        return status.name() + " " + status.label();
    }

    public static String label(Label label) {
        return label.value + " " + label.secret;
    }

    public static String fussy(Fussy fussy) {
        return "unreachable";
    }

    public enum Status {
        ACTIVE,
        RETIRED {
            @Override
            String label() {
                return "gone";
            }
        };

        String label() {
            return "here";
        }
    }

    public static class Person {
        private final String name;
        private final int age;
        private String email;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        @Override
        public String toString() {
            return name + " (" + age + ") " + email;
        }
    }

    public static class Team {
        private List<Person> members;
        private Collection<String> tags;
        private Status status;

        public void setMembers(List<Person> members) {
            this.members = members;
        }

        public void setTags(Collection<String> tags) {
            this.tags = tags;
        }

        public void setStatus(Status status) {
            this.status = status;
        }
    }

    public static class Named<T> {
        T value;

        public void setValue(T value) {
            this.value = value;
        }
    }

    public static class Label extends Named<String> {
        String secret;

        private void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Fussy {
        public void setMood(String mood) {
            throw new IllegalArgumentException("not in the mood: " + mood);
        }
    }
}
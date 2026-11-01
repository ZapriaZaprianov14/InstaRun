# InstaRun

InstaRun is an IntelliJ IDEA plugin that runs, debugs and unit-tests **a single Java method** straight from the
editor — no `main` method, no test class, no run configuration to write by hand.

Put the caret on a method name, right-click, and choose one of:

| Action | What it does |
|---|---|
| **Run Method** | Asks for the arguments (and, for an instance method, how to build the object it is called on), then runs the method in a fresh JVM and prints the result in the Run window. |
| **Debug Method** | The same, under the debugger, so breakpoints inside the method are hit. |
| **Generate Unit Test** | Runs the method once with the values you entered, records what it returned or threw, and writes a JUnit test that checks it does the same again. |

## Features

- **Any method with a body**: static or instance, public, protected, package-private or private, in any class
  of the project that can be loaded by name.
- **Input dialog per method** with one editor per parameter:
  - plain fields for primitives, their wrappers, `String`, `BigInteger`, `BigDecimal`;
  - a drop-down for enums;
  - a **JSON editor** with a generated template for objects, arrays and collections, plus a **Form** view
    that edits the same value field by field;
  - the last values entered for each method are remembered.
- **Precise error messages**: invalid input is reported per field before anything is launched
  (e.g. `Customer has no property 'nmae'. Its properties are: id, name, email.`), and methods that cannot be
  run explain why (abstract receiver, Spring bean, inner class, unsupported parameter type, …).
- **Result popup**: when the method returns an object, array, collection or map, the result opens as
  formatted, read-only JSON in a popup with a **Copy JSON** link. Plain values stay in the Run window.
- **Unit test generation** for JUnit 5, JUnit 4.13 and JUnit 4.12. The test rebuilds the same inputs as Java
  code, calls the method, and asserts on the return value field by field, on the thrown exception, or on the
  changed state of the receiver. Imports are shortened and redundant casts removed. The test can also be
  written without running the method; the assertions are then left as `// TODO`.
- **Fresh JVM every time**: the method runs with the module's own JDK and classpath, and the project is built
  first (Make before run), so the result is never stale and the IDE is never affected.

## Requirements

- IntelliJ IDEA 2025.3 or newer (Community or Ultimate), with the Java and JSON plugins (bundled).
- Projects using Java 8 or newer. The plugin itself is built with JDK 21.
- For test generation: JUnit 5 (`org.junit.jupiter:junit-jupiter`) or JUnit 4 on the module's test classpath,
  and a test source folder.

## Getting started

```bash
./gradlew runIde          # start a sandbox IDE with the plugin installed
./gradlew buildPlugin     # build build/distributions/InstaRun-<version>.zip
```

To install the built plugin into your own IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and
choose the zip.

Then open a Java file, put the caret on a method name in its declaration and right-click → **Run Method**.

## What can be entered

| Type | Editor | Example |
|---|---|---|
| `int`, `long`, `double`, `boolean`, `char`, … and wrappers | text field | `42`, `true`, `x` |
| `String` | text field | `hello` |
| `BigInteger`, `BigDecimal` | text field | `12.50` |
| enums | drop-down | `PAID` |
| arrays, `List`, `Set`, `Collection`, `Queue`, `Deque` and common implementations | JSON / Form | `[1, 2, 3]` |
| your own classes (beans) | JSON / Form | `{"id": 7, "name": "Ann"}` |

A class can be entered when it has a constructor whose parameters InstaRun can build, or setters for its
properties. InstaRun picks the constructor that takes the properties given in the JSON and uses setters for
the rest. Nested objects are supported; templates go three levels deep.

## How it works

InstaRun works in **two processes**:

1. **In the IDE**, the plugin reads the method through PSI, describes every parameter type (`TypeDescriptor`),
   shows the dialog and turns what was entered into a serializable description of the values (`ValueSpec`).
   It then creates a temporary run configuration that starts a new JVM.
2. **In the new JVM**, a small bootstrap (`InstaRunBootstrap`, compiled for Java 8 and put first on the
   classpath) decodes the request, builds the values by reflection, calls the method and prints the result.
   When a test is generated or the result popup is wanted, it also records the outcome to a file, which the
   plugin reads back with a deserialization allowlist.

Exit codes of the bootstrap: `0` returned normally, `1` the user's code threw, `2` InstaRun could not run
the method (e.g. the class is out of date).

## Testing

```bash
./gradlew bootstrapTest   # 103 tests of the bootstrap, no IDE needed (JUnit 4)
./gradlew test            # 67 plugin tests: 50 plain unit tests + 17 IntelliJ platform tests
./gradlew check           # both
```

## Project layout

```
src/
├── bootstrap/java/com/instarun/      runs in the user's JVM (Java 8, no IDE classes)
│   ├── bootstrap/                    InstaRunBootstrap, request codec, Observer, Outcome, ObservationJson
│   ├── json/                         JsonNode, JsonBinder, JsonSkeleton, JsonWriter
│   └── reflection/                   TypeDescriptor, ValueSpec, RunBlocker, descriptors
├── bootstrapTest/java/               bootstrap tests
├── main/java/com/instarun/           the IntelliJ plugin
│   ├── ui/                           actions, input dialog, editors, forms, result popup
│   ├── reflection/                   PSI → descriptors (MethodDescriptorFactory, TypeDescriptorFactory)
│   ├── json/                         JsonPsiReader (JSON PSI → JsonNode)
│   ├── launch/                       run configuration, launcher, outcome reader, result listener
│   └── codegen/                      test generation (TestGenerator, writers, TestFileWriter)
├── main/resources/                   plugin.xml, MessageBundler.properties
└── test/java/                        plugin tests
```

## Limitations

- Instance methods of abstract classes, interfaces, enums, inner classes and Spring beans cannot be run yet.
- Maps, interfaces and abstract types cannot be entered as arguments (maps are shown in results).
- Constructors and plain getters/setters are not offered.
- Generated assertions reflect one run: methods that depend on time, randomness or external state need the
  test adjusted by hand.

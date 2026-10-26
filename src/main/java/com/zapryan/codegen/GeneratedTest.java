package com.zapryan.codegen;

import java.util.Set;

/**
 * A generated test method, with class names fully qualified for the IDE to shorten once it is inserted.
 *
 * @param staticImports the assertion methods it calls unqualified, to import from the framework's assertions class
 */
public record GeneratedTest(String methodText, Set<String> staticImports) {
}
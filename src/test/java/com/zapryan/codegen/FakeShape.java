package com.zapryan.codegen;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A hand-built {@link TypeShape}, standing in for what the IDE reads from PSI. */
final class FakeShape implements TypeShape {

    private final Kind kind;
    private final String text;
    private boolean accessible = true;
    private @Nullable TypeShape elementType;
    private @Nullable TypeShape mapValueType;
    private final List<Accessor> accessors = new ArrayList<>();

    private FakeShape(Kind kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    static FakeShape primitive(String name) {
        return new FakeShape(Kind.PRIMITIVE, name);
    }

    static FakeShape boxed(String simpleName) {
        return new FakeShape(Kind.BOXED, "java.lang." + simpleName);
    }

    static FakeShape string() {
        return new FakeShape(Kind.STRING, "java.lang.String");
    }

    static FakeShape enumType(String text) {
        return new FakeShape(Kind.ENUM, text);
    }

    static FakeShape object(String text) {
        return new FakeShape(Kind.OTHER, text);
    }

    static FakeShape array(TypeShape component) {
        FakeShape shape = new FakeShape(Kind.ARRAY, component.getText() + "[]");
        shape.elementType = component;
        return shape;
    }

    static FakeShape list(TypeShape element) {
        return container(Kind.LIST, "java.util.List", element);
    }

    static FakeShape set(TypeShape element) {
        return container(Kind.SET, "java.util.Set", element);
    }

    static FakeShape collection(TypeShape element) {
        return container(Kind.COLLECTION, "java.util.Collection", element);
    }

    static FakeShape map(TypeShape key, TypeShape value) {
        FakeShape shape = new FakeShape(Kind.MAP, "java.util.Map<" + key.getText() + ", " + value.getText() + ">");
        shape.mapValueType = value;
        return shape;
    }

    private static FakeShape container(Kind kind, String name, TypeShape element) {
        FakeShape shape = new FakeShape(kind, name + "<" + element.getText() + ">");
        shape.elementType = element;
        return shape;
    }

    FakeShape with(String accessor, TypeShape type) {
        accessors.add(new Accessor(accessor, type));
        return this;
    }

    FakeShape inaccessible() {
        accessible = false;
        return this;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public boolean isAccessible() {
        return accessible;
    }

    @Override
    public @Nullable TypeShape getElementType() {
        return elementType;
    }

    @Override
    public @Nullable TypeShape getMapValueType() {
        return mapValueType;
    }

    @Override
    public List<Accessor> getAccessors() {
        return accessors;
    }
}
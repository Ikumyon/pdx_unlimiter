package com.paradox_challenges.eu4_unlimiter.parser;

public class ValueNode<T> extends Node {

    private T value;

    public ValueNode(T value) {
        this.value = value;
    }

    public String toString(int indentation) {
        return getValue().toString();
    }

    public T getValue() {
        return value;
    }
}

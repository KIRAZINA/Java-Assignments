package com.example.collections;

import java.util.Objects;
import java.util.StringJoiner;

public class MyCollectionList implements CollectionList {
    private String[] elements;
    private int size;

    public MyCollectionList() {
        this(10);
    }

    public MyCollectionList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Capacity must be non-negative");
        }
        this.elements = new String[initialCapacity];
        this.size = 0;
    }

    private void ensureCapacity(int minCapacity) {
        int oldLength = elements.length;
        int newCapacity = Math.max(minCapacity, oldLength + oldLength / 2);

        // Overflow protection
        if (newCapacity < 0) {
            newCapacity = Integer.MAX_VALUE;
        }

        if (newCapacity > oldLength) {
            String[] newArray = new String[newCapacity];
            for (int i = 0; i < size; i++) {
                newArray[i] = elements[i];
            }
            elements = newArray;
        }
    }

    @Override
    public boolean add(String o) {
        ensureCapacity(size + 1);
        elements[size++] = o;
        return true; // Always true unless OutOfMemoryError
    }

    @Override
    public boolean add(int index, String o) {
        if (index < 0 || index > size) return false;
        ensureCapacity(size + 1);
        for (int i = size; i > index; i--) {
            elements[i] = elements[i - 1];
        }
        elements[index] = o;
        size++;
        return true;
    }

    @Override
    public boolean set(int index, String o) {
        if (index < 0 || index >= size) return false;
        elements[index] = o;
        return true;
    }

    @Override
    public boolean delete(String o) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(elements[i], o)) {
                for (int j = i; j < size - 1; j++) {
                    elements[j] = elements[j + 1];
                }
                elements[size--] = null; // More readable than elements[--size]
                return true;
            }
        }
        return false;
    }

    @Override
    public String get(int index) {
        if (index < 0 || index >= size) return null;
        return elements[index];
    }

    @Override
    public boolean contains(String o) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(elements[i], o)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(CollectionList collection) {
        if (collection == null || collection.size() != this.size) return false;

        if (collection instanceof MyCollectionList) {
            MyCollectionList other = (MyCollectionList) collection;
            for (int i = 0; i < size; i++) {
                if (!Objects.equals(this.elements[i], other.elements[i])) return false;
            }
            return true;
        }

        for (int i = 0; i < size; i++) {
            if (!Objects.equals(this.get(i), collection.get(i))) return false;
        }
        return true;
    }

    @Override
    public boolean clear() {
        size = 0;
        return true;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < size; i++) {
            joiner.add(Objects.toString(elements[i]));
        }
        return joiner.toString();
    }
}

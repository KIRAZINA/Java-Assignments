package com.example.collections;

/**
 * Simple dynamic collection interface for String elements.
 */
public interface CollectionList {
    /**
     * Adds element to the end.
     * @param o element to add
     * @return true if element was added
     */
    boolean add(String o);

    /**
     * Inserts element at index, shifting subsequent elements to the right.
     * @param index target index (0..size)
     * @param o element to insert
     * @return true if element was inserted
     */
    boolean add(int index, String o);

    /**
     * Replaces element at index.
     * @param index target index (0..size-1)
     * @param o new element
     * @return true if replacement succeeded
     */
    boolean set(int index, String o);

    /**
     * Deletes first occurrence of the element (including null).
     * @param o element to delete
     * @return true if an element was deleted
     */
    boolean delete(String o);

    /**
     * Returns element at index or null if index is invalid.
     * @param index target index
     * @return element or null
     */
    String get(int index);

    /**
     * Checks whether the list contains the element (including null).
     * @param o element to check
     * @return true if present
     */
    boolean contains(String o);

    /**
     * Compares content and order with another CollectionList.
     * @param collection other collection
     * @return true if equal by elements and order
     */
    boolean equals(CollectionList collection);

    /**
     * Clears logical content (size becomes 0).
     * @return true
     */
    boolean clear();

    /**
     * Returns current number of elements.
     * @return size
     */
    int size();
}

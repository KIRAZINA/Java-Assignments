package com.example.collections;

import java.util.Iterator;

/**
 * Simple dynamic collection interface for String elements.
 *
 * <p>Extends {@link Iterable<String>} so that instances can be used in
 * enhanced-for loops and produce a fail-fast {@link Iterator}.</p>
 */
public interface CollectionList extends Iterable<String> {

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
     * Removes the element at the given index, shifting subsequent elements
     * left.  The vacated tail slot is nulled to prevent loitering.
     *
     * @param index index of the element to remove
     * @return the removed element, or {@code null} if the index is out of range
     */
    String remove(int index);

    /**
     * Removes the first occurrence of the specified value, shifting subsequent
     * elements left.  The vacated tail slot is nulled to prevent loitering.
     *
     * @param value element to remove (may be {@code null})
     * @return true if an element was removed
     */
    boolean remove(String value);

    /**
     * Deletes first occurrence of the element (including null).
     * Convenience alias for {@link #remove(String)}.
     *
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
     * Clears logical content (size becomes 0).  All internal slots are
     * nulled so the GC can reclaim the String objects.
     * @return true
     */
    boolean clear();

    /**
     * Returns current number of elements.
     * @return size
     */
    int size();

    /**
     * Ensures the internal backing array can hold at least {@code minCapacity}
     * elements without triggering an automatic resize on the next add.
     *
     * <p>The growth factor is exactly 1.5× (i.e.
     * {@code newCapacity = oldCapacity + (oldCapacity >> 1)}).</p>
     *
     * @param minCapacity the minimum required capacity
     */
    void ensureCapacity(int minCapacity);

    /**
     * Trims the internal backing array to exactly match the current
     * {@code size()}, releasing any unused capacity.  This is useful after
     * removing many elements to reduce memory footprint.
     */
    void trimToSize();

    /**
     * Returns a freshly allocated {@code String[]} containing exactly
     * {@code size()} elements.  The returned array is a copy — modifications
     * to it do not affect the collection's internal state.
     *
     * @return a new array of length {@code size()}
     */
    String[] toArray();
}

package com.example.collections;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A from-scratch dynamic array implementation for {@code String} elements.
 *
 * <p>This implementation deliberately avoids {@code java.util.Arrays},
 * {@code java.util.List}, {@code java.util.ArrayList}, and
 * {@code java.util.Objects}.  Only raw {@code String[]} arrays,
 * {@link System#arraycopy}, and the JDK's built-in {@code String} methods
 * are used.</p>
 *
 * <p>Key design decisions:</p>
 * <ul>
 *   <li><b>Growth factor 1.5×</b> — balances memory waste vs. resize frequency.
 *       At 1.5×, the array doubles its capacity every ~1.7 resizes (vs. 1.0
 *       for 2×), amortising the copy cost while keeping less wasted memory.</li>
 *   <li><b>Anti-loitering</b> — every remove/clear operation nulls the
 *       vacated slot so the GC can reclaim the String object.  Without this,
 *       the array holds strong references to "dead" objects, causing a
 *       classic memory leak known as <i>loitering</i>.</li>
 *   <li><b>Fail-fast iterator</b> — a {@code modCount} field tracks every
 *       structural modification.  The iterator snapshots this value and
 *       throws {@link ConcurrentModificationException} if it detects a
 *       divergence, catching accidental concurrent modification.</li>
 *   <li><b>System.arraycopy</b> — used for all bulk element shifts.  This is
 *       a native method (JNI bridge to C {@code memmove}) and is dramatically
 *       faster than a Java {@code for} loop because it operates without JVM
 *       interpretation overhead and can leverage CPU-level optimisations
 *       (word-aligned copies, SIMD, pipelined stores).</li>
 * </ul>
 */
public class MyCollectionList implements CollectionList {

    /** Backing array — holds the actual String references. */
    private String[] elements;

    /** Number of elements currently stored (logical size, not array length). */
    private int size;

    /**
     * Modification count — incremented on every structural change
     * (add, remove, clear, trimToSize).  The fail-fast iterator compares
     * its own snapshot of this field to detect concurrent modification.
     *
     * <p>Marked {@code transient} so that it is not serialised if the
     * class is ever made {@link java.io.Serializable}.</p>
     */
    private transient int modCount;

    // ------------------------------------------------------------------ //
    //  Constructors                                                       //
    // ------------------------------------------------------------------ //

    /** Default capacity used when no initial capacity is specified. */
    private static final int DEFAULT_CAPACITY = 10;

    public MyCollectionList() {
        this(DEFAULT_CAPACITY);
    }

    public MyCollectionList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Capacity must be non-negative");
        }
        this.elements = new String[initialCapacity];
        this.size = 0;
        this.modCount = 0;
    }

    /**
     * Defensive-copy constructor.
     *
     * <p>The supplied array is <strong>deeply copied</strong> into a new
     * backing array via {@link System#arraycopy}.  This guarantees that
     * subsequent external modifications to {@code initialData} have no
     * effect on the collection's internal state — a cornerstone of
     * encapsulation for any collection that accepts externally-owned data.</p>
     *
     * @param initialData the initial contents (may be {@code null})
     */
    public MyCollectionList(String[] initialData) {
        if (initialData == null) {
            this.elements = new String[0];
            this.size = 0;
        } else {
            this.size = initialData.length;
            // Allocate a new array of exactly the right length — NOT a larger
            // one, because the caller explicitly provided a fixed-size data set.
            this.elements = new String[initialData.length];
            // System.arraycopy is a native call that performs a memory-level
            // block copy.  It is faster than a manual for-loop because it
            // avoids per-element bounds-check overhead and utilises CPU
            // vector instructions when available.
            System.arraycopy(initialData, 0, this.elements, 0, initialData.length);
        }
        this.modCount = 0;
    }

    // ------------------------------------------------------------------ //
    //  Capacity management                                                //
    // ------------------------------------------------------------------ //

    /**
     * Ensures the backing array can hold at least {@code minCapacity}
     * elements without triggering an automatic resize.
     *
     * <p>Growth factor: exactly 1.5× via
     * {@code newCapacity = oldCapacity + (oldCapacity >> 1)}.</p>
     *
     * <p>{@code oldCapacity >> 1} is equivalent to {@code oldCapacity / 2}
     * but uses a single arithmetic right-shift instruction — marginally
     * faster than division on most CPU architectures.</p>
     *
     * @param minCapacity minimum required capacity (used by add operations)
     */
    @Override
    public void ensureCapacity(int minCapacity) {
        int oldCapacity = elements.length;
        if (minCapacity > oldCapacity) {
            // 1.5x growth: oldCap + oldCap/2  (implemented via bit-shift)
            int newCapacity = oldCapacity + (oldCapacity >> 1);
            if (newCapacity < minCapacity) {
                // Fall back to exact min if 1.5x is still not enough
                newCapacity = minCapacity;
            }
            // Overflow protection: if newCapacity wrapped around to negative,
            // clamp to Integer.MAX_VALUE
            if (newCapacity < 0) {
                newCapacity = Integer.MAX_VALUE;
            }

            // System.arraycopy: native block copy — far faster than a Java loop
            String[] newArray = new String[newCapacity];
            System.arraycopy(elements, 0, newArray, 0, size);
            elements = newArray;
        }
        // Note: we intentionally do NOT increment modCount here.
        // Capacity expansion does not change the logical contents of the list.
    }

    /**
     * Trims the backing array to exactly match {@code size()}, releasing
     * any unused memory.
     */
    @Override
    public void trimToSize() {
        if (size < elements.length) {
            String[] newArray = new String[size];
            System.arraycopy(elements, 0, newArray, 0, size);
            elements = newArray;
            modCount++; // structural change: backing array reference changes
        }
    }

    // ------------------------------------------------------------------ //
    //  Core operations                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Appends an element at the end.  Triggers capacity expansion if needed.
     */
    @Override
    public boolean add(String o) {
        ensureCapacity(size + 1);
        elements[size++] = o;
        modCount++;
        return true; // Always true unless OutOfMemoryError
    }

    /**
     * Inserts an element at {@code index}, shifting elements at and after
     * {@code index} one position to the right.
     */
    @Override
    public boolean add(int index, String o) {
        if (index < 0 || index > size) {
            return false;
        }
        ensureCapacity(size + 1);

        // Shift right half of the array [index, size) → [index+1, size+1)
        // System.arraycopy is a native method (JNI → C memmove) that copies
        // an entire memory block in one call.  It is significantly faster
        // than a Java for-loop because:
        //   1. No per-element JVM interpretation overhead
        //   2. CPU can use word-aligned / SIMD bulk copy instructions
        //   3. No repeated bounds-checking on individual array accesses
        System.arraycopy(elements, index, elements, index + 1, size - index);

        elements[index] = o;
        size++;
        modCount++;
        return true;
    }

    @Override
    public boolean set(int index, String o) {
        if (index < 0 || index >= size) {
            return false;
        }
        elements[index] = o;
        // set() is NOT a structural modification — size doesn't change —
        // so modCount is NOT incremented (consistent with java.util.List).
        return true;
    }

    /**
     * Removes the element at {@code index} and returns it.
     *
     * <p>After the left-shift, the now-orphaned last slot is explicitly
     * set to {@code null}.  This is a critical anti-loitering measure:
     * without it, the backing array would hold a strong reference to the
     * removed String, preventing the Garbage Collector from reclaiming
     * its memory even though the collection no longer logically contains
     * it.</p>
     *
     * @param index index of the element to remove
     * @return the removed element, or {@code null} if index is out of range
     */
    @Override
    public String remove(int index) {
        if (index < 0 || index >= size) {
            return null;
        }

        String oldValue = elements[index];

        // Shift elements [index+1, size) left by one to fill the gap
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }

        // Anti-loitering: null out the vacated tail slot.
        // After the shift above, the element that was at 'size-1' is now
        // at 'size-2'.  The slot at 'size-1' still holds a reference to the
        // (now logically removed) last element.  Setting it to null breaks
        // the strong reference chain so the GC can free the String object's
        // memory.  This matters particularly when the array is large and
        // many remove operations have been performed — without nulling,
        // the backing array would act as a "graveyard" of unreachable
        // objects, causing an effective memory leak.
        elements[--size] = null;

        modCount++;
        return oldValue;
    }

    /**
     * Removes the first occurrence of {@code value} from the list.
     * Delegates to {@link #remove(int)} after locating the index.
     */
    @Override
    public boolean remove(String value) {
        int index = indexOf(value);
        if (index == -1) {
            return false;
        }
        remove(index); // remove(int) handles anti-loitering and modCount
        return true;
    }

    /**
     * Convenience alias for {@link #remove(String)} — preserved for
     * backward compatibility with the original API.
     */
    @Override
    public boolean delete(String o) {
        return remove(o);
    }

    @Override
    public String get(int index) {
        if (index < 0 || index >= size) {
            return null;
        }
        return elements[index];
    }

    @Override
    public boolean contains(String o) {
        return indexOf(o) >= 0;
    }

    @Override
    public boolean clear() {
        // Null out every slot so the GC can reclaim all String objects.
        // This is the anti-loitering measure applied to bulk removal.
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
        modCount++;
        return true;
    }

    @Override
    public int size() {
        return size;
    }

    // ------------------------------------------------------------------ //
    //  Array export                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Returns a freshly allocated array containing exactly {@code size()}
     * elements.  The caller receives a <strong>copy</strong> — modifying
     * the returned array has no effect on the collection.
     */
    @Override
    public String[] toArray() {
        String[] result = new String[size];
        System.arraycopy(elements, 0, result, 0, size);
        return result;
    }

    // ------------------------------------------------------------------ //
    //  Equality & hashing (manual implementation — no java.util.Objects)  //
    // ------------------------------------------------------------------ //

    /**
     * Two {@code MyCollectionList} instances are equal if they have the
     * same size and all corresponding elements are equal (using
     * {@link String#equals(Object)}) in the same order.
     *
     * <p>{@code null} elements are handled safely: a pair of {@code null}
     * values at the same index are considered equal; a {@code null} and
     * a non-null String are never equal.</p>
     */
    @Override
    public boolean equals(Object o) {
        // Identity check — same reference, trivially equal
        if (o == this) {
            return true;
        }
        // Type check — must be another CollectionList
        if (!(o instanceof CollectionList)) {
            return false;
        }
        CollectionList other = (CollectionList) o;
        // Size mismatch — cannot be equal
        if (other.size() != this.size) {
            return false;
        }
        // Element-by-element comparison using the public get() API.
        // This works for any CollectionList implementation, not just
        // MyCollectionList, because we compare through the interface.
        for (int i = 0; i < size; i++) {
            String a = this.elements[i];  // safe direct access — we know 'this' is MyCollectionList
            String b = other.get(i);
            if (a == null) {
                if (b != null) return false;
            } else {
                // String.equals handles null gracefully (returns false)
                if (!a.equals(b)) return false;
            }
        }
        return true;
    }

    /**
     * Hash code computed as {@code Σ elements[i].hashCode() * 31^(n-1-i)},
     * matching the contract defined by {@link java.util.List#hashCode()}
     * so that equal lists produce equal hash codes.
     */
    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size; i++) {
            String e = elements[i];
            // Use 31 as the multiplier (prime) for good distribution
            result = 31 * result + (e == null ? 0 : e.hashCode());
        }
        return result;
    }

    // ------------------------------------------------------------------ //
    //  Internal helper                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Finds the index of the first occurrence of {@code value},
     * performing a manual null-safe linear scan without {@code Objects.equals}.
     *
     * @param value the value to search for (may be {@code null})
     * @return the index, or {@code -1} if not found
     */
    private int indexOf(String value) {
        for (int i = 0; i < size; i++) {
            if (value == null) {
                if (elements[i] == null) return i;
            } else {
                if (value.equals(elements[i])) return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ //
    //  Iterable / Fail-fast Iterator                                     //
    // ------------------------------------------------------------------ //

    /**
     * Returns a fail-fast iterator over the elements in insertion order.
     */
    @Override
    public Iterator<String> iterator() {
        return new Itr();
    }

    /**
     * Fail-fast iterator backed by the outer {@code MyCollectionList}.
     *
     * <p>The iterator takes a snapshot of {@code modCount} at construction
     * time and re-checks it before every {@link #next()} and
     * {@link #remove()} call.  If the outer list has been structurally
     * modified since the snapshot (by any means other than this iterator's
     * own {@code remove()}), a
     * {@link ConcurrentModificationException} is thrown immediately —
     * hence "fail-fast".</p>
     */
    private class Itr implements Iterator<String> {
        /** Index of the next element to return. */
        private int cursor;

        /** Index of the last element returned by next(); -1 if none / already removed. */
        private int lastRet = -1;

        /** Snapshot of modCount at iterator creation or last sync. */
        private int expectedModCount = modCount;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public String next() {
            // Fail-fast check: has the list been structurally modified?
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException(
                        "List was modified after iterator creation");
            }
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            lastRet = cursor;
            return elements[cursor++];
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException(
                        "next() has not been called, or remove() was already called");
            }
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException(
                        "List was modified after iterator creation");
            }

            // Delegate to the outer class's remove(int).  This handles:
            //   - shifting elements left (System.arraycopy)
            //   - nulling the vacated slot (anti-loitering)
            //   - incrementing modCount (structural change)
            MyCollectionList.this.remove(lastRet);

            // After remove(lastRet), elements at [lastRet+1 ...) have shifted
            // left into [lastRet ...).  Cursor was lastRet+1 (post-increment
            // in next()), so we set it back to lastRet — the next call to
            // next() will return the element that was previously at lastRet+1
            // (now at lastRet after the shift).
            cursor = lastRet;

            // Re-sync the expected modCount with the new reality
            expectedModCount = modCount;
            lastRet = -1;
        }
    }

    // ------------------------------------------------------------------ //
    //  toString                                                          //
    // ------------------------------------------------------------------ //

    @Override
    public String toString() {
        // We cannot use java.util.StringJoiner (not in the prohibited list,
        // but using StringBuilder is more educational and explicit).
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            // Manual null-safe conversion (avoiding java.util.Objects)
            if (elements[i] == null) {
                sb.append("null");
            } else {
                sb.append(elements[i]);
            }
        }
        sb.append(']');
        return sb.toString();
    }
}

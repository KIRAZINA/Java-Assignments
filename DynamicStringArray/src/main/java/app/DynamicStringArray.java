package app;

public class DynamicStringArray {

    private static final int INITIAL_CAPACITY = 10;

    // Internal array for storing elements
    private String[] data;

    // Number of actually stored elements
    private int size;

    public DynamicStringArray() {
        data = new String[INITIAL_CAPACITY];
        size = 0;
    }

    // Returns current number of elements
    public int size() {
        return size;
    }

    // Returns true if collection contains no elements
    public boolean isEmpty() {
        return size == 0;
    }

    // Adds element to the end of the array
    public void add(String value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    // Inserts element at specified index (0..size)
    public void add(int index, String value) {
        checkIndexForAdd(index);
        ensureCapacity(size + 1);

        System.arraycopy(data, index, data, index + 1, size - index);
        data[index] = value;
        size++;
    }

    // Returns element at index
    public String get(int index) {
        checkIndex(index);
        return data[index];
    }

    // Replaces element at index and returns old value
    public String set(int index, String value) {
        checkIndex(index);
        String old = data[index];
        data[index] = value;
        return old;
    }

    // Removes element at index and returns it
    public String remove(int index) {
        checkIndex(index);

        String removed = data[index];
        int elementsToMove = size - index - 1;

        if (elementsToMove > 0) {
            System.arraycopy(data, index + 1, data, index, elementsToMove);
        }

        // Clear last reference to avoid memory leaks
        data[--size] = null;
        return removed;
    }

    // Removes first occurrence of the specified value
    public boolean remove(String value) {
        int index = indexOf(value);
        if (index >= 0) {
            remove(index);
            return true;
        }
        return false;
    }

    /**
     * Clears the collection.
     * After calling this method, the internal array capacity
     * is reset to INITIAL_CAPACITY.
     */
    public void clear() {
        data = new String[INITIAL_CAPACITY];
        size = 0;
    }

    // Returns index of first occurrence or -1 if not found
    public int indexOf(String value) {
        for (int i = 0; i < size; i++) {
            if (equalsValue(data[i], value)) {
                return i;
            }
        }
        return -1;
    }

    // --- Helper methods ---

    // Ensures internal array can hold at least minCapacity elements
    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= data.length) {
            return;
        }

        int newCapacity = data.length * 2;
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        String[] newData = new String[newCapacity];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }

    // Validates index for get/set/remove
    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Size: " + size
            );
        }
    }

    // Validates index for add (index may equal size)
    private void checkIndexForAdd(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Size: " + size
            );
        }
    }

    // Null-safe equality check for indexOf/remove
    private boolean equalsValue(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }
}

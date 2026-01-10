package app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicStringArrayTest {

    private DynamicStringArray arr;

    @BeforeEach
    void setUp() {
        arr = new DynamicStringArray();
    }

    @Test
    void testInitialState() {
        assertEquals(0, arr.size());
        assertTrue(arr.isEmpty());
    }

    @Test
    void testAddAndGet() {
        arr.add("Apple");
        arr.add("Banana");
        arr.add("Cherry");

        assertEquals(3, arr.size());
        assertEquals("Apple", arr.get(0));
        assertEquals("Banana", arr.get(1));
        assertEquals("Cherry", arr.get(2));
    }

    @Test
    void testAddAtIndex() {
        arr.add("Apple");
        arr.add("Banana");
        arr.add(1, "Orange");

        assertEquals(3, arr.size());
        assertEquals("Apple", arr.get(0));
        assertEquals("Orange", arr.get(1));
        assertEquals("Banana", arr.get(2));
    }

    // Boundary cases for add(index)
    @Test
    void testAddAtIndexZeroInEmptyArray() {
        arr.add(0, "A");
        assertEquals(1, arr.size());
        assertEquals("A", arr.get(0));
    }

    @Test
    void testAddAtIndexSizeInsertAtEnd() {
        arr.add("X");
        arr.add("Y");
        arr.add(arr.size(), "Z");
        assertEquals(3, arr.size());
        assertEquals("Z", arr.get(2));
    }

    @Test
    void testSet() {
        arr.add("Apple");
        arr.add("Banana");

        String old = arr.set(1, "Grapes");
        assertEquals("Banana", old);
        assertEquals("Grapes", arr.get(1));
    }

    @Test
    void testRemoveByIndex() {
        arr.add("Apple");
        arr.add("Banana");
        arr.add("Cherry");

        String removed = arr.remove(1);
        assertEquals("Banana", removed);
        assertEquals(2, arr.size());
        assertEquals("Apple", arr.get(0));
        assertEquals("Cherry", arr.get(1));
    }

    // Remove last element
    @Test
    void testRemoveLastElement() {
        arr.add("A");
        arr.add("B");
        arr.add("C");
        String removed = arr.remove(arr.size() - 1);
        assertEquals("C", removed);
        assertEquals(2, arr.size());
        assertEquals("B", arr.get(1));
    }

    @Test
    void testRemoveByValue() {
        arr.add("Apple");
        arr.add("Banana");
        arr.add("Cherry");

        assertTrue(arr.remove("Banana"));
        assertEquals(2, arr.size());
        assertEquals(-1, arr.indexOf("Banana"));

        assertFalse(arr.remove("NotExist"));
    }

    // Remove(String) with duplicates
    @Test
    void testRemoveStringWithDuplicates() {
        arr.add("A");
        arr.add("B");
        arr.add("B");
        arr.add("C");

        boolean removed = arr.remove("B");
        assertTrue(removed);
        assertEquals(3, arr.size());
        assertEquals("B", arr.get(1)); // second B shifted left
        assertEquals("C", arr.get(2));
    }

    @Test
    void testIndexOf() {
        arr.add("Apple");
        arr.add("Banana");
        arr.add("Cherry");

        assertEquals(1, arr.indexOf("Banana"));
        assertEquals(-1, arr.indexOf("Orange"));

        arr.add(null);
        assertEquals(3, arr.indexOf(null));
    }

    @Test
    void testClear() {
        arr.add("Apple");
        arr.add("Banana");
        arr.clear();

        assertEquals(0, arr.size());
        assertTrue(arr.isEmpty());
        assertEquals(-1, arr.indexOf("Apple"));
    }

    @Test
    void testEnsureCapacityExpansion() {
        for (int i = 0; i < 15; i++) {
            arr.add("Item" + i);
        }
        assertEquals(15, arr.size());
        assertEquals("Item14", arr.get(14));
    }

    @Test
    void testIndexValidation() {
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(0));
        arr.add("Apple");
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> arr.add(-1, "Banana"));
    }

    // Exception tests for remove(int) and set(int)
    @Test
    void testRemoveInvalidIndexThrows() {
        arr.add("A");
        assertThrows(IndexOutOfBoundsException.class, () -> arr.remove(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> arr.remove(arr.size()));
    }

    @Test
    void testSetInvalidIndexThrows() {
        arr.add("A");
        assertThrows(IndexOutOfBoundsException.class, () -> arr.set(arr.size(), "X"));
    }
}

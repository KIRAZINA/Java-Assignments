package com.example.collections;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyCollectionListTest {

    @Test
    void testAddAndSize() {
        CollectionList list = new MyCollectionList(2);
        assertTrue(list.add("A"));
        assertTrue(list.add("B"));
        assertEquals(2, list.size());
        assertTrue(list.add("C")); // triggers grow()
        assertEquals(3, list.size());
    }

    @Test
    void testAddByIndex() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        list.add("B");
        assertTrue(list.add(1, "X"));
        assertEquals("[A, X, B]", list.toString());
        assertEquals("X", list.get(1));
    }

    @Test
    void testSet() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        list.add("B");
        assertTrue(list.set(1, "C"));
        assertEquals("C", list.get(1));
        assertFalse(list.set(5, "Z")); // invalid index
    }

    @Test
    void testDelete() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        list.add("B");
        list.add("C");
        assertTrue(list.delete("B"));
        assertEquals("[A, C]", list.toString());
        assertFalse(list.delete("X")); // not present
    }

    @Test
    void testDeleteNull() {
        CollectionList list = new MyCollectionList();
        list.add(null);
        list.add("A");
        assertTrue(list.contains(null));
        assertTrue(list.delete(null));
        assertFalse(list.contains(null));
    }

    @Test
    void testContains() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        assertTrue(list.contains("A"));
        assertFalse(list.contains("B"));
    }

    @Test
    void testEquals() {
        CollectionList list1 = new MyCollectionList();
        list1.add("A");
        list1.add("B");

        CollectionList list2 = new MyCollectionList();
        list2.add("A");
        list2.add("B");

        assertTrue(list1.equals(list2));

        list2.set(1, "C");
        assertFalse(list1.equals(list2));
    }

    @Test
    void testClear() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        list.add("B");
        assertEquals(2, list.size());
        list.clear();
        assertEquals(0, list.size());
        assertEquals("[]", list.toString());
    }

    @Test
    void testToString() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        list.add("B");
        assertEquals("[A, B]", list.toString());
    }

    @Test
    void testAddIndexTriggersGrowth() {
        CollectionList list = new MyCollectionList(2);
        list.add("A");
        list.add("B");
        // capacity reached, now insert in the middle
        assertTrue(list.add(1, "X"));
        assertEquals("[A, X, B]", list.toString());
        assertEquals(3, list.size());
    }

    @Test
    void testNegativeIndex() {
        CollectionList list = new MyCollectionList();
        list.add("A");
        // negative index should fail gracefully
        assertFalse(list.add(-1, "X"));
        assertFalse(list.set(-5, "Y"));
        assertNull(list.get(-2));
    }

    @Test
    void testEmptyCollection() {
        CollectionList list = new MyCollectionList();
        assertEquals(0, list.size());
        assertNull(list.get(0)); // invalid index
        assertFalse(list.contains("A"));
        assertFalse(list.delete("A"));
        assertEquals("[]", list.toString());
    }
}

package app;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous tests for {@link DynamicStringArray}: 2x growth, bulk addAll,
 * null-safe indexOf/contains/remove, compaction, removeRange and precise index
 * boundary behaviour.
 */
class DynamicStringArrayTest {

    private DynamicStringArray arr;

    @BeforeEach
    void setUp() {
        arr = new DynamicStringArray();
    }

    // Helper: read the private 'data' field via reflection.
    private static String[] extractData(DynamicStringArray a) throws Exception {
        Field f = DynamicStringArray.class.getDeclaredField("data");
        f.setAccessible(true);
        return (String[]) f.get(a);
    }

    // Helper: read the private 'size' field via reflection.
    private static int extractSize(DynamicStringArray a) throws Exception {
        Field f = DynamicStringArray.class.getDeclaredField("size");
        f.setAccessible(true);
        return (int) f.get(a);
    }

    // ---------------------------------------------------------------
    // Basic CRUD (retained coverage)
    // ---------------------------------------------------------------

    @Test
    void testAddAndGet() {
        arr.add("Apple");
        arr.add("Banana");
        arr.add("Cherry");
        assertEquals(3, arr.size());
        assertEquals("Apple", arr.get(0));
        assertEquals("Cherry", arr.get(2));
    }

    @Test
    void testAddAtIndexAndAppend() {
        arr.add("A");
        arr.add("C");
        arr.add(1, "B");
        assertEquals(3, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("B", arr.get(1));
        assertEquals("C", arr.get(2));
        // append via index == size
        arr.add(arr.size(), "D");
        assertEquals("D", arr.get(3));
    }

    @Test
    void testSet() {
        arr.add("A");
        arr.add("B");
        assertEquals("B", arr.set(1, "Z"));
        assertEquals("Z", arr.get(1));
    }

    @Test
    void testRemoveByIndex() {
        arr.add("A");
        arr.add("B");
        arr.add("C");
        assertEquals("B", arr.remove(1));
        assertEquals(2, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("C", arr.get(1));
    }

    @Test
    void testClear() {
        arr.add("A");
        arr.add("B");
        arr.clear();
        assertEquals(0, arr.size());
        assertTrue(arr.isEmpty());
    }

    @Test
    void testContains() {
        arr.add("A");
        arr.add(null);
        assertTrue(arr.contains("A"));
        assertTrue(arr.contains(null));
        assertFalse(arr.contains("Z"));
    }

    // ---------------------------------------------------------------
    // Task 1: 2x doubling growth
    // ---------------------------------------------------------------

    @Test
    void test2xGrowth() throws Exception {
        // Default capacity is 10. Fill it exactly (no resize yet).
        for (int i = 0; i < 10; i++) {
            arr.add("x" + i);
        }
        String[] data = extractData(arr);
        assertEquals(10, data.length, "capacity should still be 10 after exactly 10 inserts");
        // The 11th element must trigger EXACTLY a 2x resize to 20 (not 15).
        arr.add("x10");
        data = extractData(arr);
        assertEquals(20, data.length, "2x growth must yield capacity 20, not 15");
    }

    // ---------------------------------------------------------------
    // Task 1: Bulk addAll (single resize)
    // ---------------------------------------------------------------

    @Test
    void testAddAllBulkSingleResize() throws Exception {
        String[] items = new String[1000];
        for (int i = 0; i < 1000; i++) items[i] = "s" + i;

        int capBefore = extractData(arr).length; // 10
        arr.addAll(items);

        int capAfter = extractData(arr).length;
        // A single resize should jump straight to the exact needed capacity
        // (size 0 + 1000 new items = 1000), proving there was only ONE resize.
        assertEquals(1000, capAfter, "addAll should do exactly ONE resize to capacity 1000");
        assertEquals(1000, arr.size());
        assertEquals("s0", arr.get(0));
        assertEquals("s999", arr.get(999));
    }

    @Test
    void testAddAllAtMiddle() {
        arr.add("A");
        arr.add("D");
        arr.addAll(1, new String[]{"B", "C"});
        assertEquals(4, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("B", arr.get(1));
        assertEquals("C", arr.get(2));
        assertEquals("D", arr.get(3));
    }

    @Test
    void testAddAllNullArrayThrows() {
        assertThrows(NullPointerException.class, () -> arr.addAll((String[]) null));
    }

    // ---------------------------------------------------------------
    // Task 2: Null-safe indexOf / remove / duplicates
    // ---------------------------------------------------------------

    @Test
    void testNullSafeIndexOf() {
        arr.add("a");
        arr.add("b");
        arr.add(null);
        arr.add("c");
        arr.add(null);
        arr.add("d");
        assertEquals(2, arr.indexOf(null), "indexOf(null) must find first null at index 2");
        assertEquals(0, arr.indexOf("a"));
        assertEquals(-1, arr.indexOf("nonexistent"));
    }

    @Test
    void testDuplicateRemoval() {
        arr.add("A");
        arr.add("B");
        arr.add("A");
        arr.add("C");
        assertTrue(arr.remove("A"));
        // Only the FIRST "A" (index 0) is removed -> [B, A, C]
        assertEquals(3, arr.size());
        assertEquals("B", arr.get(0));
        assertEquals("A", arr.get(1));
        assertEquals("C", arr.get(2));
    }

    @Test
    void testRemoveNull() {
        arr.add("A");
        arr.add(null);
        arr.add("B");
        arr.add(null);
        assertTrue(arr.remove(null));
        assertEquals(3, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("B", arr.get(1));
        // A second null is still present (shifted into index 2), so removal succeeds.
        assertTrue(arr.remove(null));
        assertEquals(2, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("B", arr.get(1));
        // Now no nulls remain.
        assertFalse(arr.remove(null));
    }

    // ---------------------------------------------------------------
    // Task 4: compact
    // ---------------------------------------------------------------

    @Test
    void testCompact() {
        arr.add("A");
        arr.add(null);
        arr.add("B");
        arr.add(null);
        arr.add("C");
        int removed = arr.compact();
        assertEquals(2, removed, "compact must report 2 nulls removed");
        assertEquals(3, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("B", arr.get(1));
        assertEquals("C", arr.get(2));
    }

    @Test
    void testCompactNoNulls() {
        arr.add("A");
        arr.add("B");
        assertEquals(0, arr.compact());
        assertEquals(2, arr.size());
    }

    // ---------------------------------------------------------------
    // Task 4: removeRange
    // ---------------------------------------------------------------

    @Test
    void testRemoveRange() {
        for (int i = 0; i < 10; i++) arr.add("e" + i);
        // Remove indices [3, 7) -> e3, e4, e5, e6 gone; tail e7,e8,e9 shifts left.
        arr.removeRange(3, 7);
        assertEquals(6, arr.size());
        assertEquals("e0", arr.get(0));
        assertEquals("e2", arr.get(2));
        assertEquals("e7", arr.get(3));
        assertEquals("e8", arr.get(4));
        assertEquals("e9", arr.get(5));
    }

    @Test
    void testRemoveRangeInvalid() {
        for (int i = 0; i < 5; i++) arr.add("e" + i);
        assertThrows(IndexOutOfBoundsException.class, () -> arr.removeRange(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> arr.removeRange(2, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> arr.removeRange(3, 1));
    }

    // ---------------------------------------------------------------
    // Task 3: Index boundary precision
    // ---------------------------------------------------------------

    @Test
    void testAddAtSizeSucceedsButGetSizeThrows() {
        arr.add("A");
        arr.add("B");
        // appending at index == size is allowed
        arr.add(arr.size(), "C");
        assertEquals("C", arr.get(2));
        // but reading at index == size is not (no element there)
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(arr.size()));
        // inserting beyond size is illegal
        assertThrows(IndexOutOfBoundsException.class, () -> arr.add(arr.size() + 1, "X"));
    }

    @Test
    void testSetInvalidIndexThrows() {
        arr.add("A");
        assertThrows(IndexOutOfBoundsException.class, () -> arr.set(arr.size(), "X"));
    }

    @Test
    void testRemoveFromEmptyAndInvalid() {
        // removing from empty / out of range
        assertThrows(IndexOutOfBoundsException.class, () -> arr.remove(0));
        arr.add("A");
        assertThrows(IndexOutOfBoundsException.class, () -> arr.remove(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> arr.remove(arr.size()));
    }

    // ---------------------------------------------------------------
    // Capacity methods
    // ---------------------------------------------------------------

    @Test
    void testEnsureCapacityAndTrimToSize() throws Exception {
        for (int i = 0; i < 10; i++) arr.add("x");
        arr.ensureCapacity(50);
        assertEquals(50, extractData(arr).length);
        arr.trimToSize(); // back to exactly size (10)
        assertEquals(10, extractData(arr).length);
    }
}

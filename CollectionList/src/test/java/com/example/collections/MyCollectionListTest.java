package com.example.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test suite for MyCollectionList, covering:
 *
 * <p>1. Basic add/get/set/delete operations</p>
 * <p>2. Edge cases (negative indices, empty collections, nulls)</p>
 * <p>3. Growth / capacity mechanics (Reflection-based)</p>
 * <p>4. Anti-loitering (Reflection-based null-slot verification)</p>
 * <p>5. Fail-fast iterator behaviour</p>
 * <p>6. Defensive copying in constructors</p>
 * <p>7. equals / hashCode contracts</p>
 */
@DisplayName("MyCollectionList Tests")
class MyCollectionListTest {

    private MyCollectionList list;

    @BeforeEach
    void setUp() {
        list = new MyCollectionList();
    }

    // ------------------------------------------------------------------ //
    //  Helpers for Reflection-based tests                                 //
    // ------------------------------------------------------------------ //

    /** Reads the private {@code elements} backing array via reflection. */
    private String[] getInternalArray(MyCollectionList list) throws Exception {
        Field field = MyCollectionList.class.getDeclaredField("elements");
        field.setAccessible(true);
        return (String[]) field.get(list);
    }

    /** Reads the private {@code size} field via reflection. */
    private int getInternalSize(MyCollectionList list) throws Exception {
        Field field = MyCollectionList.class.getDeclaredField("size");
        field.setAccessible(true);
        return field.getInt(list);
    }

    // ------------------------------------------------------------------ //
    //  Basic tests (preserved from original)                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

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

    // ------------------------------------------------------------------ //
    //  Task 1: Memory Mechanics & Anti-Loitering                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Anti-Loitering Tests")
    class AntiLoiteringTests {

        @Test
        @DisplayName("Loitering: after removing 50 of 100 elements, slots 50-99 must be null")
        void shouldNullVacatedSlotsAfterRemove() throws Exception {
            MyCollectionList list = new MyCollectionList();
            for (int i = 0; i < 100; i++) {
                list.add("item" + i);
            }

            // Remove the first 50 elements by index (0, 0, 0, ... to trigger shifting)
            for (int i = 0; i < 50; i++) {
                list.remove(0);
            }

            assertEquals(50, list.size());

            // Use reflection to inspect the internal backing array
            String[] internal = getInternalArray(list);

            // Slots 50 through 99 must be null — this proves anti-loitering
            for (int i = 50; i < internal.length; i++) {
                assertNull(internal[i],
                        "Slot " + i + " should be null to allow GC to reclaim the String object");
            }
        }

        @Test
        @DisplayName("Loitering: clear() must null all slots")
        void shouldNullAllSlotsAfterClear() throws Exception {
            MyCollectionList list = new MyCollectionList(20);
            for (int i = 0; i < 20; i++) {
                list.add("item" + i);
            }
            list.clear();
            assertEquals(0, list.size());

            String[] internal = getInternalArray(list);
            for (int i = 0; i < internal.length; i++) {
                assertNull(internal[i],
                        "Slot " + i + " should be null after clear() to prevent loitering");
            }
        }

        @Test
        @DisplayName("Loitering: remove(String) must null the vacated tail")
        void shouldNullVacatedSlotAfterRemoveByValue() throws Exception {
            MyCollectionList list = new MyCollectionList();
            for (int i = 0; i < 10; i++) {
                list.add("item" + i);
            }

            // Remove the last element by value
            list.remove("item9");

            String[] internal = getInternalArray(list);
            assertNull(internal[9],
                    "Last slot must be null after remove-by-value to prevent loitering");
        }
    }

    @Nested
    @DisplayName("Capacity Tests")
    class CapacityTests {

        @Test
        @DisplayName("Capacity boundary: adding 10 items to capacity-10 list should not resize")
        void shouldNotResizeWhenAtCapacity() throws Exception {
            MyCollectionList list = new MyCollectionList(10);
            for (int i = 0; i < 10; i++) {
                list.add("item" + i);
            }
            assertEquals(10, list.size());

            String[] internal = getInternalArray(list);
            assertEquals(10, internal.length,
                    "Backing array should remain at original capacity when not exceeded");
        }

        @Test
        @DisplayName("Capacity boundary: 11th item triggers 1.5x resize to exactly 15")
        void shouldResizeToExactly15On11thAdd() throws Exception {
            MyCollectionList list = new MyCollectionList(10);
            for (int i = 0; i < 10; i++) {
                list.add("item" + i);
            }
            // Trigger resize
            list.add("trigger");

            String[] internal = getInternalArray(list);
            // 1.5x of 10 = 10 + 5 = 15
            assertEquals(15, internal.length,
                    "Growth factor should be 1.5x: 10 + (10 >> 1) = 15");
        }

        @Test
        @DisplayName("trimToSize() should shrink backing array to exact size")
        void shouldTrimToExactSize() throws Exception {
            MyCollectionList list = new MyCollectionList(100);
            list.add("one");
            list.add("two");
            assertEquals(100, getInternalArray(list).length);

            list.trimToSize();
            assertEquals(2, getInternalArray(list).length,
                    "trimToSize() should shrink array to exactly match size");
            assertEquals(2, list.size());
        }

        @Test
        @DisplayName("ensureCapacity() should pre-expand without changing size")
        void shouldEnsureCapacityWithoutChangingSize() throws Exception {
            MyCollectionList list = new MyCollectionList(5);
            list.ensureCapacity(50);

            String[] internal = getInternalArray(list);
            assertTrue(internal.length >= 50,
                    "ensureCapacity(50) should expand the backing array to at least 50 slots");
            assertEquals(0, list.size(), "Size should still be 0 after ensureCapacity");
        }

        @Test
        @DisplayName("Multiple resizes follow 1.5x growth factor")
        void shouldFollow1_5xGrowthFactorAcrossResizes() throws Exception {
            MyCollectionList list = new MyCollectionList(10);
            for (int i = 0; i < 10; i++) {
                list.add("item" + i);
            }
            // 1st resize: 10 → 15
            list.add("trigger1");
            assertEquals(15, getInternalArray(list).length);

            // Fill to 15, then 16th triggers resize: 15 → 15 + 7 = 22
            for (int i = 11; i < 15; i++) {
                list.add("item" + i);
            }
            list.add("trigger2"); // 16th item → resize
            assertEquals(22, getInternalArray(list).length,
                    "1.5x of 15 = 15 + (15 >> 1) = 15 + 7 = 22");
        }
    }

    // ------------------------------------------------------------------ //
    //  Task 2: Fail-Fast Iterator                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Fail-Fast Iterator Tests")
    class FailFastIteratorTests {

        @Test
        @DisplayName("Iterator should iterate all elements in order")
        void shouldIterateAllElementsInOrder() {
            list.add("A");
            list.add("B");
            list.add("C");

            Iterator<String> it = list.iterator();
            assertTrue(it.hasNext());
            assertEquals("A", it.next());
            assertEquals("B", it.next());
            assertEquals("C", it.next());
            assertFalse(it.hasNext());
        }

        @Test
        @DisplayName("Iterator should throw ConcurrentModificationException when list is modified externally")
        void shouldThrowOnConcurrentModification() {
            list.add("A");
            list.add("B");
            list.add("C");

            Iterator<String> it = list.iterator();
            assertEquals("A", it.next());

            // Structurally modify the list after iterator creation
            list.add("D");

            assertThatThrownBy(it::next)
                    .isInstanceOf(ConcurrentModificationException.class)
                    .hasMessageContaining("modified");
        }

        @Test
        @DisplayName("Iterator should throw ConcurrentModificationException when remove is called after external add")
        void shouldThrowOnConcurrentModificationDuringRemove() {
            list.add("A");
            list.add("B");

            Iterator<String> it = list.iterator();
            it.next();

            // External modification
            list.remove(0);

            assertThatThrownBy(it::remove)
                    .isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("Iterator.remove() should correctly shift elements and sync modCount")
        void shouldIterateCorrectlyAfterIteratorRemove() {
            list.add("A");
            list.add("B");
            list.add("C");
            list.add("D");

            Iterator<String> it = list.iterator();
            assertEquals("A", it.next());
            it.remove(); // remove "A"
            assertEquals("B", it.next());
            it.remove(); // remove "B"
            assertEquals("C", it.next()); // should still see "C"
            assertEquals("D", it.next());
            assertFalse(it.hasNext());

            assertEquals(2, list.size());
            assertEquals("[C, D]", list.toString());
        }

        @Test
        @DisplayName("Iterator.remove() without next() should throw IllegalStateException")
        void shouldThrowIllegalStateExceptionOnRemoveWithoutNext() {
            list.add("A");
            Iterator<String> it = list.iterator();

            assertThatThrownBy(it::remove)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Iterator.remove() called twice without next() should throw IllegalStateException")
        void shouldThrowIllegalStateExceptionOnDoubleRemove() {
            list.add("A");
            Iterator<String> it = list.iterator();
            it.next();
            it.remove();

            assertThatThrownBy(it::remove)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Iterator should be usable in enhanced for loop")
        void shouldWorkInEnhancedForLoop() {
            list.add("X");
            list.add("Y");
            list.add("Z");

            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                sb.append(s);
            }

            assertEquals("XYZ", sb.toString());
            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("Iterator should throw NoSuchElementException when exhausted")
        void shouldThrowNoSuchElementExceptionWhenExhausted() {
            list.add("A");
            Iterator<String> it = list.iterator();
            it.next();

            assertThatThrownBy(it::next)
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // ------------------------------------------------------------------ //
    //  Task 3: Deep Equality, Hashing, Defensive Copying                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Equality & Hashing Tests")
    class EqualityHashingTests {

        @Test
        @DisplayName("Two lists with same elements should be equal and have same hashCode")
        void shouldProduceEqualHashCodes() {
            MyCollectionList list1 = new MyCollectionList();
            MyCollectionList list2 = new MyCollectionList();

            list1.add("A");
            list1.add("B");
            list1.add("C");

            list2.add("A");
            list2.add("B");
            list2.add("C");

            assertTrue(list1.equals(list2));
            assertEquals(list1.hashCode(), list2.hashCode());
        }

        @Test
        @DisplayName("Lists with different elements should not be equal")
        void shouldNotBeEqualWithDifferentElements() {
            MyCollectionList list1 = new MyCollectionList();
            MyCollectionList list2 = new MyCollectionList();

            list1.add("A");
            list2.add("B");

            assertFalse(list1.equals(list2));
        }

        @Test
        @DisplayName("Lists with different sizes should not be equal")
        void shouldNotBeEqualWithDifferentSizes() {
            MyCollectionList list1 = new MyCollectionList();
            MyCollectionList list2 = new MyCollectionList();

            list1.add("A");
            list1.add("B");
            list2.add("A");

            assertFalse(list1.equals(list2));
        }

        @Test
        @DisplayName("List should be equal to itself")
        void shouldBeEqualToItself() {
            list.add("A");
            assertTrue(list.equals(list));
        }

        @Test
        @DisplayName("List should handle null elements in equality")
        void shouldHandleNullElementsInEquality() {
            MyCollectionList list1 = new MyCollectionList();
            MyCollectionList list2 = new MyCollectionList();

            list1.add("A");
            list1.add(null);
            list1.add("C");

            list2.add("A");
            list2.add(null);
            list2.add("C");

            assertTrue(list1.equals(list2));
            assertEquals(list1.hashCode(), list2.hashCode());
        }

        @Test
        @DisplayName("List compared with null should return false")
        void shouldReturnFalseWhenComparedWithNull() {
            list.add("A");
            assertFalse(list.equals(null));
        }

        @Test
        @DisplayName("List compared with different type should return false")
        void shouldReturnFalseWhenComparedWithDifferentType() {
            list.add("A");
            assertFalse(list.equals("not a list"));
        }
    }

    @Nested
    @DisplayName("Defensive Copying Tests")
    class DefensiveCopyTests {

        @Test
        @DisplayName("Constructor should defensively copy the input array")
        void shouldDefensivelyCopyOnConstruction() {
            String[] original = {"A", "B", "C"};
            MyCollectionList list = new MyCollectionList(original);

            // Modify the original array
            original[0] = "HACKED";
            original[1] = "HACKED";

            // The list's contents should be unchanged
            assertEquals("A", list.get(0),
                    "Defensive copy should protect list from external array modifications");
            assertEquals("B", list.get(1));
            assertEquals("C", list.get(2));
            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("Constructor with null array should create empty list")
        void shouldHandleNullArrayInConstructor() {
            MyCollectionList list = new MyCollectionList((String[]) null);
            assertEquals(0, list.size());
            assertEquals("[]", list.toString());
        }

        @Test
        @DisplayName("toArray() should return a fresh copy, not the internal array")
        void shouldReturnFreshCopyFromToArray() {
            list.add("A");
            list.add("B");
            list.add("C");

            String[] array1 = list.toArray();
            String[] array2 = list.toArray();

            assertNotSame(array1, array2, "toArray() should return new arrays each time");
            assertNotSame(array1, list.toArray(), "toArray() should not return internal array reference");

            // Modify the returned array — should not affect the list
            array1[0] = "MODIFIED";
            assertEquals("A", list.get(0),
                    "Modifying toArray() result should not affect the list");
        }

        @Test
        @DisplayName("toArray() should return exactly size elements with no trailing nulls")
        void shouldReturnExactSizeArray() {
            MyCollectionList list = new MyCollectionList(50); // large backing array
            list.add("A");
            list.add("B");

            String[] array = list.toArray();
            assertEquals(2, array.length, "toArray() should return array of exact size");
        }
    }

    @Nested
    @DisplayName("Remove Operations")
    class RemoveOperations {

        @Test
        @DisplayName("remove(int) should return the removed element")
        void shouldReturnRemovedElement() {
            list.add("A");
            list.add("B");
            list.add("C");

            String removed = list.remove(1);
            assertEquals("B", removed);
            assertEquals("[A, C]", list.toString());
            assertEquals(2, list.size());
        }

        @Test
        @DisplayName("remove(int) with invalid index should return null")
        void shouldReturnNullForInvalidIndex() {
            list.add("A");
            assertNull(list.remove(-1));
            assertNull(list.remove(5));
            assertEquals(1, list.size(), "List should be unchanged after invalid remove");
        }

        @Test
        @DisplayName("remove(String) should remove first occurrence")
        void shouldRemoveFirstOccurrence() {
            list.add("A");
            list.add("B");
            list.add("A");
            list.add("C");

            assertTrue(list.remove("A"));
            assertEquals("[B, A, C]", list.toString());
            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("remove(String) for non-existent element should return false")
        void shouldReturnFalseForNonExistentElement() {
            list.add("A");
            assertFalse(list.remove("Z"));
            assertEquals(1, list.size());
        }

        @Test
        @DisplayName("remove(String null) should remove first null element")
        void shouldRemoveNullElement() {
            list.add("A");
            list.add(null);
            list.add("B");

            assertTrue(list.remove(null));
            assertEquals(2, list.size());
            assertFalse(list.contains(null));
        }

        @Test
        @DisplayName("remove(int) on last element should null the tail")
        void shouldNullTailAfterRemovingLastElement() throws Exception {
            list.add("A");
            list.add("B");
            list.add("C");

            list.remove(2); // remove last element

            String[] internal = getInternalArray(list);
            // After removing last element and shifting (nothing to shift),
            // the slot at index 2 should be null
            assertNull(internal[2],
                    "Removed element's slot should be nulled to prevent loitering");
        }
    }

    @Nested
    @DisplayName("Structural Modification & modCount Tests")
    class ModCountTests {

        @Test
        @DisplayName("add() should increment modCount (visible via fail-fast iterator)")
        void addShouldTriggerFailFast() {
            list.add("A");
            Iterator<String> it = list.iterator();
            it.next();
            list.add("B");
            assertThatThrownBy(it::next).isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("remove(int) should increment modCount")
        void removeIntShouldTriggerFailFast() {
            list.add("A");
            list.add("B");
            Iterator<String> it = list.iterator();
            it.next();
            list.remove(0);
            assertThatThrownBy(it::next).isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("remove(String) should increment modCount")
        void removeStringShouldTriggerFailFast() {
            list.add("A");
            list.add("B");
            Iterator<String> it = list.iterator();
            it.next();
            list.remove("A");
            assertThatThrownBy(it::next).isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("clear() should increment modCount")
        void clearShouldTriggerFailFast() {
            list.add("A");
            list.add("B");
            Iterator<String> it = list.iterator();
            it.next();
            list.clear();
            assertThatThrownBy(it::next).isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("set() should NOT increment modCount (not structural)")
        void setShouldNotTriggerFailFast() {
            list.add("A");
            list.add("B");
            Iterator<String> it = list.iterator();
            it.next(); // returns "A", cursor now at 1
            list.set(0, "X"); // set is not structural — should NOT throw
            assertEquals("B", it.next()); // iterator continues unaffected
            assertEquals("X", list.get(0)); // set() took effect on the list
        }

        @Test
        @DisplayName("trimToSize() should increment modCount")
        void trimToSizeShouldTriggerFailFast() {
            MyCollectionList list = new MyCollectionList(50);
            list.add("A");
            Iterator<String> it = list.iterator();
            it.next();
            list.trimToSize();
            assertThatThrownBy(it::next).isInstanceOf(ConcurrentModificationException.class);
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        @Test
        @DisplayName("Should handle adding 1000 elements (multiple resizes)")
        void shouldHandleAddingManyElements() {
            MyCollectionList list = new MyCollectionList();
            for (int i = 0; i < 1000; i++) {
                assertTrue(list.add("item" + i));
            }
            assertEquals(1000, list.size());
            assertEquals("item0", list.get(0));
            assertEquals("item999", list.get(999));
        }

        @Test
        @DisplayName("Should handle adding elements in the middle efficiently")
        void shouldHandleMiddleInserts() {
            MyCollectionList list = new MyCollectionList();
            list.add("A");
            list.add("C");
            list.add("D");
            list.add(1, "B");

            assertEquals("[A, B, C, D]", list.toString());
            assertEquals(4, list.size());
        }

        @Test
        @DisplayName("Should handle interleaved add and remove operations")
        void shouldHandleInterleavedOperations() {
            MyCollectionList list = new MyCollectionList(3);
            list.add("A");
            list.add("B");
            list.add("C");
            list.add("D"); // triggers resize to 4 (3 + 1, or 3 + 1 = 4.5 -> 4? Actually 3 + (3>>1) = 3+1=4)
            assertEquals(4, list.size());

            list.remove(1); // remove "B"
            assertEquals("[A, C, D]", list.toString());

            list.add(1, "X");
            assertEquals("[A, X, C, D]", list.toString());

            list.remove("C");
            assertEquals("[A, X, D]", list.toString());
            assertEquals(3, list.size());
        }
    }
}

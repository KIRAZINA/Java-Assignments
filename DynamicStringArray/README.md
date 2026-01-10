# DynamicStringArray

A minimal, dependency-free implementation of a dynamic array for strings, built on top of a plain `String[]`. It mimics a subset of `ArrayList<String>` behavior without using Java Collections or Streams, while strictly following the contracts defined below.

---

## Overview

- Stores elements in a resizable `String[]`.
- Automatically grows capacity when adding elements.
- Supports indexed insert, get, set, remove by index, remove by value, clear, and search (`indexOf`).
- Enforces index validation and throws `IndexOutOfBoundsException` on contract violations.
- Handles `null` values safely for search and removal.
- Uses only `System.arraycopy(...)` for copying, no `Arrays.copyOf` or Collections.

---

## Public API

- `int size()`
    - Returns the number of stored elements (logical size), not array capacity.

- `boolean isEmpty()`
    - Returns `true` if `size == 0`.

- `void add(String value)`
    - Appends an element to the end.
    - Grows capacity if needed.

- `void add(int index, String value)`
    - Inserts an element at `index`.
    - Valid indices: `0..size` (inclusive).
    - Shifts elements to the right.
    - Grows capacity if needed.
    - Throws `IndexOutOfBoundsException` if `index` is out of range.

- `String get(int index)`
    - Returns element at `index`.
    - Valid indices: `0..size-1`.
    - Throws `IndexOutOfBoundsException` if `index` is out of range.

- `String set(int index, String value)`
    - Replaces element at `index` with `value`.
    - Returns the old value.
    - Valid indices: `0..size-1`.
    - Throws `IndexOutOfBoundsException` if `index` is out of range.

- `String remove(int index)`
    - Removes element at `index`.
    - Shifts elements to the left to fill the gap.
    - Returns the removed element.
    - Ensures the last occupied slot is set to `null` to avoid holding references.
    - Valid indices: `0..size-1`.
    - Throws `IndexOutOfBoundsException` if `index` is out of range.

- `boolean remove(String value)`
    - Removes the first occurrence of `value`.
    - Comparison via `equals`, `null` handled safely.
    - Returns `true` if removal happened, otherwise `false`.

- `void clear()`
    - Resets the collection: `size = 0`.
    - Recreates the internal array with the initial capacity to drop retained references and capacity.
    - Note: an alternative approach is to keep capacity and null out slots. This implementation resets capacity intentionally.

- `int indexOf(String value)`
    - Returns index of the first occurrence; `-1` if not found.
    - Comparison via `equals`, `null` handled safely.

---

## Implementation details

- Initial capacity: `INITIAL_CAPACITY = 10`.
- Capacity growth: doubles the current capacity when not enough space; ensures capacity is at least `minCapacity`.
- Copying: strictly uses `System.arraycopy(...)`.
- Index checks:
    - `checkIndex(int index)` for `get`, `set`, `remove`.
    - `checkIndexForAdd(int index)` for `add(index)`; `index == size` is allowed.
- Null-safe equality: `equalsValue(String a, String b)` ensures correct behavior for `null`.

---

## Code structure

- `src/main/java/com/example/DynamicStringArray.java`
- `src/test/java/com/example/DynamicStringArrayTest.java`

The test suite uses JUnit 5 and covers:
- Basic operations: add, add(index), get, set, remove(index), remove(value), indexOf, clear.
- Boundary cases: `add(0, ...)` on empty, `add(size, ...)` at end.
- Capacity growth: adding more than the initial capacity.
- Duplicates: `remove(String)` removes the first occurrence and shifts correctly.
- Exceptions: invalid indices for `get`, `add(index)`, `remove(index)`, `set(index)`.

Run tests with Maven:
- `mvn test`

---

## Example usage

```markdown
DynamicStringArray arr = new DynamicStringArray();
arr.add("Apple");
arr.add(1, "Banana");
String old = arr.set(1, "Grapes");  // returns "Banana"
String removed = arr.remove(0);     // returns "Apple"
int idx = arr.indexOf("Grapes");    // returns 0
boolean ok = arr.remove("Grapes");  // returns true
arr.clear();                        // resets size and capacity
```

---

## Constraints

- Not allowed:
    - Java Collections (`ArrayList`, `List`, `Set`, `Map`), Stream API, `Arrays.copyOf`.
- Allowed:
    - `String[]`, `System.arraycopy(...)`, basic Java exceptions.

---

## Notes

- This implementation intentionally resets capacity on `clear()` to minimize memory retention. If you prefer to keep the current capacity for performance reasons, modify `clear()` to null out elements and retain the array.
- `null` values are allowed and treated as valid elements for `indexOf` and `remove(String)`.

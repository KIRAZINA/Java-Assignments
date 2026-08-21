package app;

/**
 * A minimal, blazing-fast dynamic array of {@code String} backed by a raw
 * {@code String[]}. In deliberate contrast to CollectionList (which uses 1.5x
 * growth), this class grows by EXACTLY 2x (bitwise left shift) to favour raw
 * speed over memory frugality.
 *
 * <p>Hard constraints respected by every method:
 * <ul>
 *   <li>NO {@code java.util.Arrays}, {@code java.util.List},
 *       {@code java.util.ArrayList}, {@code java.util.Objects} or streams.</li>
 *   <li>The ONLY bulk copy primitive is {@link System#arraycopy}.</li>
 *   <li>Growth factor is exactly 2x.</li>
 *   <li>Index validation is split into two precise methods
 *       ({@link #checkIndex} and {@link #checkIndexForAdd}).</li>
 * </ul>
 */
public class DynamicStringArray {

    private static final int INITIAL_CAPACITY = 10;

    /** Internal backing array; its length is the CURRENT CAPACITY. */
    private String[] data;

    /** Number of logically occupied elements (0..data.length). */
    private int size;

    public DynamicStringArray() {
        // Default capacity is fixed at 10; growth then proceeds by 2x.
        data = new String[INITIAL_CAPACITY];
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    /**
     * Append {@code value} at the end.
     *
     * <p>PERFORMANCE: each append is amortised O(1). The rare resize doubles the
     * capacity in a single {@link System#arraycopy}; because growth is 2x, the
     * amortised cost of all resizes stays O(n) over n appends.
     */
    public void add(String value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    /**
     * Insert {@code value} at {@code index} (valid range {@code 0..size}).
     * {@code index == size} is permitted and behaves like {@link #add(String)}.
     */
    public void add(int index, String value) {
        checkIndexForAdd(index);
        ensureCapacity(size + 1);
        // Shift the tail right by one to open the slot. Single arraycopy.
        int moveCount = size - index;
        if (moveCount > 0) {
            System.arraycopy(data, index, data, index + 1, moveCount);
        }
        data[index] = value;
        size++;
    }

    public String get(int index) {
        checkIndex(index);
        return data[index];
    }

    /** Replace element at {@code index}, returning the previous value. */
    public String set(int index, String value) {
        checkIndex(index);
        String old = data[index];
        data[index] = value;
        return old;
    }

    /**
     * Remove and return the element at {@code index}. The tail shifts left in a
     * single {@link System#arraycopy}, and the vacated last slot is nulled
     * (anti-loitering) so the GC can reclaim the referenced {@code String}.
     */
    public String remove(int index) {
        checkIndex(index);
        String removed = data[index];
        int moveCount = size - index - 1;
        if (moveCount > 0) {
            System.arraycopy(data, index + 1, data, index, moveCount);
        }
        data[--size] = null; // clear the now-unused reference
        return removed;
    }

    /**
     * Remove the FIRST occurrence of {@code value}.
     *
     * <p>Returns {@code true} if something was removed, {@code false} otherwise.
     * Null is handled exactly like a real value (removes the first {@code null}
     * element). See {@link #equalsValue} for the null-safe comparison rationale.
     */
    public boolean remove(String value) {
        int index = indexOf(value);
        if (index >= 0) {
            remove(index);
            return true;
        }
        return false;
    }

    public void clear() {
        data = new String[INITIAL_CAPACITY];
        size = 0;
    }

    // ------------------------------------------------------------------
    // Search (null-safe)
    // ------------------------------------------------------------------

    /**
     * Return the index of the first element equal to {@code value}, or -1.
     *
     * <p>NULL-SAFETY: {@code indexOf(null)} returns the index of the first
     * {@code null} element. We cannot use {@code Objects.equals} (the constraint
     * forbids {@code java.util.*}), so we implement the identical reference-aware
     * comparison manually: two values are equal iff both are {@code null} or
     * the non-null one {@code .equals} the other.
     */
    public int indexOf(String value) {
        for (int i = 0; i < size; i++) {
            if (equalsValue(data[i], value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether {@code value} is present, using the same null-safe semantics as
     * {@link #indexOf(String)}.
     */
    public boolean contains(String value) {
        return indexOf(value) >= 0;
    }

    // ------------------------------------------------------------------
    // Bulk operations (Task 1) - minimise arraycopy calls
    // ------------------------------------------------------------------

    /**
     * Append every element of {@code items} to the end.
     *
     * <p>WHY BULK IS FASTER: a naive loop of {@code add()} would potentially
     * resize the array on every element, each resize being a full O(n) copy ->
     * O(n * m) overall (quadratic) where m is the number of items. By computing
     * the target capacity up front we perform EXACTLY ONE resize (and possibly
     * none) and a single {@link System#arraycopy}, making this O(n + m).
     */
    public void addAll(String[] items) {
        if (items == null) {
            throw new NullPointerException("items must not be null");
        }
        int numNew = items.length;
        if (numNew == 0) return;
        // Single resize to the exact required capacity (no incremental growth).
        ensureCapacity(size + numNew);
        System.arraycopy(items, 0, data, size, numNew);
        size += numNew;
    }

    /**
     * Insert every element of {@code items} at {@code index}.
     *
     * <p>Exactly one resize (if needed) followed by a single right-shift of the
     * existing tail and a single copy of the new items - no per-element resizes.
     */
    public void addAll(int index, String[] items) {
        if (items == null) {
            throw new NullPointerException("items must not be null");
        }
        checkIndexForAdd(index);
        int numNew = items.length;
        if (numNew == 0) return;
        ensureCapacity(size + numNew); // SINGLE resize
        int moveCount = size - index;
        if (moveCount > 0) {
            // Shift the existing tail right by numNew to open the gap.
            System.arraycopy(data, index, data, index + numNew, moveCount);
        }
        // Fill the gap with the new items.
        System.arraycopy(items, 0, data, index, numNew);
        size += numNew;
    }

    // ------------------------------------------------------------------
    // Compaction & range removal (Task 4)
    // ------------------------------------------------------------------

    /**
     * Remove all {@code null} elements, shifting surviving (non-null) elements
     * left. Uses the classic two-pointer technique (one read head, one write
     * head) so the whole pass is a single O(n) scan.
     *
     * @return the number of nulls removed.
     */
    public int compact() {
        int write = 0;
        int nullsRemoved = 0;
        for (int read = 0; read < size; read++) {
            if (data[read] != null) {
                data[write++] = data[read];
            } else {
                nullsRemoved++;
            }
        }
        // Anti-loitering: null out the stale tail [write, size) so references do
        // not linger in the backing array.
        for (int i = write; i < size; i++) {
            data[i] = null;
        }
        size = write;
        return nullsRemoved;
    }

    /**
     * Remove all elements in the half-open range {@code [fromIndex, toIndex)}.
     * The surviving tail is shifted left with a SINGLE {@link System#arraycopy}.
     */
    public void removeRange(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException(
                    "from=" + fromIndex + ", to=" + toIndex + ", size=" + size);
        }
        int numRemoved = toIndex - fromIndex;
        if (numRemoved == 0) return;
        int moveCount = size - toIndex;
        if (moveCount > 0) {
            System.arraycopy(data, toIndex, data, fromIndex, moveCount);
        }
        // Null out the now-vacated tail.
        int newSize = size - numRemoved;
        for (int i = newSize; i < size; i++) {
            data[i] = null;
        }
        size = newSize;
    }

    // ------------------------------------------------------------------
    // Capacity management (Task 1)
    // ------------------------------------------------------------------

    /**
     * Grow the backing array by EXACTLY 2x when capacity is exceeded, otherwise
     * do nothing.
     *
     * <p>GROWTH STRATEGY - 2x vs 1.5x:
     * Doubling (2x) means the capacity sequence is 10, 20, 40, 80... For small
     * arrays this leads to FEWER resizes than 1.5x (10, 15, 22, 33...), so appends
     * are faster amisortised. The cost is higher memory waste: after a resize to
     * hold N elements the next resize is triggered only at 2N, leaving up to ~50%
     * of the array unused. 1.5x trims that waste to ~33% but pays for it with
     * more frequent, costlier reallocations. This class deliberately chooses
     * SPEED (2x). The 2x is implemented as a bitwise left shift ({@code << 1}),
     * which is a single, extremely cheap CPU instruction.
     */
    public void ensureCapacity(int minCapacity) {
        if (minCapacity <= data.length) {
            return;
        }
        // Exactly 2x growth via bitwise left shift.
        int newCapacity = data.length << 1;
        // If 2x still falls short (e.g. a huge bulk addAll), jump straight to
        // the requested capacity so we never need a second resize.
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }
        String[] newData = new String[newCapacity];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }

    /**
     * Shrink the backing array to exactly {@link #size()} elements, reclaiming
     * any excess capacity.
     */
    public void trimToSize() {
        if (data.length == size) return;
        String[] newData = new String[size];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }

    // ------------------------------------------------------------------
    // Benchmark (Task 4)
    // ------------------------------------------------------------------

    /**
     * Micro-benchmark comparing one-by-one appends against a single bulk
     * {@link #addAll(String[])}. Prints timing in nanoseconds to stdout.
     * (Designed for demonstration; not a statistically rigorous benchmark.)
     */
    public static void benchmark() {
        final int N = 10000;
        int runs = 5;
        long oneByOneTotal = 0;
        long bulkTotal = 0;

        for (int r = 0; r < runs; r++) {
            DynamicStringArray a = new DynamicStringArray();
            long start = System.nanoTime();
            for (int i = 0; i < N; i++) {
                a.add("s" + i);
            }
            oneByOneTotal += (System.nanoTime() - start);

            String[] items = new String[N];
            for (int i = 0; i < N; i++) {
                items[i] = "s" + i;
            }
            DynamicStringArray b = new DynamicStringArray();
            long start2 = System.nanoTime();
            b.addAll(items);
            bulkTotal += (System.nanoTime() - start2);
        }

        System.out.println("DynamicStringArray benchmark (" + N + " elements x " + runs + " runs):");
        System.out.println("  One-by-one add():    " + oneByOneTotal + " ns");
        System.out.println("  Bulk addAll():       " + bulkTotal + " ns");
        System.out.println("  Bulk is approx " + (oneByOneTotal / (double) Math.max(bulkTotal, 1))
                + "x faster");
    }

    // ------------------------------------------------------------------
    // Index validation (Task 3)
    // ------------------------------------------------------------------

    /**
     * Validate an index for READ/UPDATE/REMOVE access.
     *
     * <p>Permitted range is {@code 0 <= index < size}. {@code size} is NOT valid
     * here because there is no element at that position - it is past the end.
     */
    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /**
     * Validate an index for INSERT access.
     *
     * <p>Permitted range is {@code 0 <= index <= size}. Unlike {@link #checkIndex},
     * {@code index == size} IS allowed: inserting at position {@code size} means
     * "append at the very end", which is legal. {@code get(size)} must fail
     * because that slot holds no element yet - this asymmetry is why the two
     * checks are deliberately separate methods rather than one shared validator.
     */
    private void checkIndexForAdd(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /**
     * Manual null-safe equality.
     *
     * <p>We implement this by hand instead of {@code Objects.equals} because the
     * project constraints forbid {@code java.util.*}. The logic mirrors it
     * exactly: two references are equal when both are {@code null}, or when the
     * first is non-null and {@code .equals} the second.
     */
    private boolean equalsValue(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DynamicStringArray[size=").append(size)
                .append(", capacity=").append(data.length).append("]{");
        for (int i = 0; i < size; i++) {
            sb.append(data[i]);
            if (i + 1 < size) sb.append(", ");
        }
        return sb.append("}").toString();
    }
}

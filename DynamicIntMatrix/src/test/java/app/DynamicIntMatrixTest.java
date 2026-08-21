package app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous tests for {@link DynamicIntMatrix}, {@link MatrixIO} and
 * {@link MatrixUtils}: core operations, mathematical boundary enforcement,
 * binary serialization (including null rows), CSV escaping and reflection-based
 * memory-leak verification.
 */
public class DynamicIntMatrixTest {

    // ---------------------------------------------------------------
    // Core CRUD
    // ---------------------------------------------------------------

    @Test
    public void testAddRowAndGetSet() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        assertEquals(1, m.rows());
        assertEquals(3, m.cols());
        assertEquals(1, m.get(0, 0));
        assertEquals(2, m.get(0, 1));
        assertEquals(3, m.get(0, 2));

        int old = m.set(0, 1, 20);
        assertEquals(2, old);
        assertEquals(20, m.get(0, 1));
    }

    @Test
    public void testAddColAndFlatten() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{10, 11});
        m.addRow(new int[]{20, 21});
        m.addCol(new int[]{100, 200});
        assertEquals(2, m.rows());
        assertEquals(3, m.cols());
        assertArrayEquals(new int[]{10, 11, 100, 20, 21, 200}, m.flattenRowMajor());
    }

    @Test
    public void testInsertRowAndRemoveRow() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 1});
        m.addRow(new int[]{3, 3});
        m.addRow(1, new int[]{2, 2}); // insert in middle
        // Aliasing bug guard: insertion must not corrupt neighbouring rows.
        assertEquals(3, m.rows());
        assertArrayEquals(new int[]{1, 1}, m.removeRow(0));
        assertEquals(2, m.rows());
        assertEquals(2, m.get(0, 0));
        assertEquals(3, m.get(1, 0));
    }

    @Test
    public void testInsertColAndRemoveCol() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        m.addCol(1, new int[]{9, 8});
        assertEquals(3, m.cols());
        assertArrayEquals(new int[]{2, 4}, m.removeCol(2));
        assertEquals(2, m.cols());
        assertEquals(9, m.get(0, 1));
        assertEquals(8, m.get(1, 1));
    }

    @Test
    public void testClearAndFill() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{7, 8});
        m.addRow(new int[]{9, 10});
        m.fill(5);
        assertArrayEquals(new int[]{5, 5, 5, 5}, m.flattenRowMajor());
        m.clear();
        assertEquals(0, m.rows());
        assertEquals(0, m.cols());
    }

    @Test
    public void testEdgeCasesInvalidArgs() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        assertThrows(NullPointerException.class, () -> m.addRow(null));
        assertThrows(NullPointerException.class, () -> m.addCol(null));
        assertThrows(IndexOutOfBoundsException.class, () -> m.removeRow(0));
        assertThrows(IndexOutOfBoundsException.class, () -> m.removeCol(0));

        m.addRow(new int[]{1, 2});
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(0, 2));
    }

    @Test
    public void testResize() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});

        m.resize(3, 3);
        assertEquals(3, m.rows());
        assertEquals(3, m.cols());
        assertEquals(0, m.get(2, 2));
        assertEquals(1, m.get(0, 0));

        m.resize(1, 1);
        assertEquals(1, m.rows());
        assertEquals(1, m.cols());
        assertEquals(1, m.get(0, 0));

        m.resize(2, 2);
        assertEquals(1, m.get(0, 0));
        assertEquals(0, m.get(0, 1));
        assertEquals(0, m.get(1, 1));
    }

    @Test
    public void stressMultipleAddsRemoves() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        for (int r = 0; r < 50; r++) {
            int[] row = new int[50];
            for (int c = 0; c < 50; c++) row[c] = r;
            m.addRow(row);
        }
        assertEquals(50, m.rows());
        assertEquals(50, m.cols());
        for (int i = 0; i < 10; i++) {
            m.removeRow(0);
            m.removeCol(m.cols() - 1);
        }
        assertEquals(40, m.rows());
        assertEquals(40, m.cols());
    }

    // ---------------------------------------------------------------
    // Task 2: Mathematical rigor & boundary enforcement
    // ---------------------------------------------------------------

    @Test
    public void testTransposeAndRotateSquare() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        DynamicIntMatrix t = m.transpose();
        assertEquals(2, t.rows());
        assertEquals(2, t.cols());
        assertArrayEquals(new int[]{1, 3, 2, 4}, t.flattenRowMajor());

        // transpose must not mutate the source
        assertEquals(2, m.rows());
        assertEquals(2, m.cols());

        m.rotate90Clockwise();
        assertArrayEquals(new int[]{3, 1, 4, 2}, m.flattenRowMajor());
    }

    @Test
    public void testRotateNonSquare() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.addRow(new int[]{4, 5, 6});
        m.rotate90Clockwise();
        assertEquals(3, m.rows());
        assertEquals(2, m.cols());
        // 2x3 -> 3x2 with rows reversed
        assertArrayEquals(new int[]{4, 1, 5, 2, 6, 3}, m.flattenRowMajor());
    }

    @Test
    public void testMathOperations() {
        DynamicIntMatrix a = new DynamicIntMatrix();
        a.addRow(new int[]{1, 2});
        a.addRow(new int[]{3, 4});

        DynamicIntMatrix b = new DynamicIntMatrix();
        b.addRow(new int[]{10, 20});
        b.addRow(new int[]{30, 40});

        assertArrayEquals(new int[]{11, 22, 33, 44}, a.add(b).flattenRowMajor());
        assertArrayEquals(new int[]{9, 18, 27, 36}, b.subtract(a).flattenRowMajor());
        assertArrayEquals(new int[]{10, 20, 30, 40}, a.multiplyByScalar(10).flattenRowMajor());

        // A (2x2) * B (2x2) = [70 100 ; 150 220]
        assertArrayEquals(new int[]{70, 100, 150, 220}, a.multiply(b).flattenRowMajor());

        // Math ops return NEW instances; original must be untouched.
        assertEquals(2, a.rows());
        assertEquals(1, a.get(0, 0));
    }

    @Test
    public void testMatrixMultiplyDimensions() {
        DynamicIntMatrix a = new DynamicIntMatrix();
        a.addRow(new int[]{1, 2, 3}); // 1x3

        DynamicIntMatrix b = new DynamicIntMatrix();
        b.addRow(new int[]{1, 2}); // 3x2
        b.addRow(new int[]{3, 4});
        b.addRow(new int[]{5, 6});

        DynamicIntMatrix res = a.multiply(b);
        assertEquals(1, res.rows());
        assertEquals(2, res.cols());
        // [1 2 3] * [[1,2],[3,4],[5,6]] = [22 28]
        assertArrayEquals(new int[]{22, 28}, res.flattenRowMajor());

        // 3x2 * 1x3 -> 2 != 1 mismatch
        assertThrows(IllegalArgumentException.class, () -> b.multiply(a));
    }

    @Test
    public void testDimensionMismatchThrows() {
        // A is 2x3, B is 2x2
        DynamicIntMatrix a = new DynamicIntMatrix();
        a.addRow(new int[]{1, 2, 3});
        a.addRow(new int[]{4, 5, 6});

        DynamicIntMatrix b = new DynamicIntMatrix();
        b.addRow(new int[]{1, 2});
        b.addRow(new int[]{3, 4});

        IllegalArgumentException addEx =
                assertThrows(IllegalArgumentException.class, () -> a.add(b));
        assertTrue(addEx.getMessage().toLowerCase().contains("dimension"),
                "message should be descriptive: " + addEx.getMessage());

        IllegalArgumentException subEx =
                assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
        assertTrue(subEx.getMessage().toLowerCase().contains("dimension"),
                "message should be descriptive: " + subEx.getMessage());

        // multiply: A.cols (3) != B.rows (2) -> undefined
        IllegalArgumentException mulEx =
                assertThrows(IllegalArgumentException.class, () -> a.multiply(b));
        assertTrue(mulEx.getMessage().toLowerCase().contains("multiplication")
                        || mulEx.getMessage().toLowerCase().contains("cols"),
                "message should be descriptive: " + mulEx.getMessage());
    }

    @Test
    public void testSubmatrixBoundary() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.addRow(new int[]{4, 5, 6});
        m.addRow(new int[]{7, 8, 9});

        // valid submatrix
        DynamicIntMatrix sub = m.getSubmatrix(0, 1, 2, 3);
        assertEquals(2, sub.rows());
        assertEquals(2, sub.cols());
        assertArrayEquals(new int[]{2, 3, 5, 6}, sub.flattenRowMajor());
        // deep copy: mutating original must not affect the submatrix
        m.set(0, 1, 99);
        assertEquals(2, sub.get(0, 0));

        // invalid: startRow > endRow
        assertThrows(IndexOutOfBoundsException.class, () -> m.getSubmatrix(2, 0, 1, 1));
        // invalid: startCol > endCol
        assertThrows(IndexOutOfBoundsException.class, () -> m.getSubmatrix(0, 2, 1, 1));
        // invalid: negative start
        assertThrows(IndexOutOfBoundsException.class, () -> m.getSubmatrix(-1, 0, 1, 1));
        // invalid: end beyond bounds
        assertThrows(IndexOutOfBoundsException.class, () -> m.getSubmatrix(0, 0, 4, 1));
    }

    @Test
    public void testDeepCopyIsIndependent() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        DynamicIntMatrix copy = m.deepCopy();
        copy.set(0, 0, 999);
        assertEquals(1, m.get(0, 0));
        assertEquals(999, copy.get(0, 0));
    }

    @Test
    public void testEnsureCapacityAndTrimToSize() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.ensureCapacity(50, 50);
        // logical size unchanged
        assertEquals(1, m.rows());
        assertEquals(3, m.cols());
        m.trimToSize();
        assertEquals(1, m.rows());
        assertEquals(3, m.cols());
        assertEquals(1, m.get(0, 0));

        // trimToSize on a shrunk matrix must not lose data
        DynamicIntMatrix big = new DynamicIntMatrix();
        for (int r = 0; r < 10; r++) big.addRow(new int[]{r, r + 1});
        big.resize(4, 2);
        big.trimToSize();
        assertEquals(4, big.rows());
        assertEquals(2, big.cols());
        assertEquals(3, big.get(3, 0));
    }

    // ---------------------------------------------------------------
    // Task 3: Bulletproof binary serialization
    // ---------------------------------------------------------------

    @Test
    public void testSerializationRoundtrip() throws Exception {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixIO.writeTo(m, baos);

        byte[] bytes = baos.toByteArray();
        DynamicIntMatrix restored = MatrixIO.readFrom(new ByteArrayInputStream(bytes));
        assertEquals(2, restored.rows());
        assertEquals(2, restored.cols());
        assertArrayEquals(m.flattenRowMajor(), restored.flattenRowMajor());
    }

    @Test
    public void testBinarySerializationRoundTripWithNullRows() throws Exception {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        m.addRow(new int[]{5, 6});
        // Inject a null row in the logical range via reflection to exercise the
        // null-row flag handling of the serializer.
        int[][] internalBefore = extractData(m);
        internalBefore[1] = null;

        byte[] bytes = m.serializeToBytes();
        DynamicIntMatrix restored = DynamicIntMatrix.deserializeFromBytes(bytes);

        assertEquals(3, restored.rows());
        assertEquals(2, restored.cols());

        int[][] internalAfter = extractData(restored);
        assertNull(internalAfter[1], "null row must be preserved across serialization");
        assertNotNull(internalAfter[0]);
        assertNotNull(internalAfter[2]);
        assertEquals(1, restored.get(0, 0));
        assertEquals(5, restored.get(2, 0));
    }

    @Test
    public void testSerializationCorruptionThrows() throws Exception {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        byte[] full = m.serializeToBytes();
        // Truncate the payload so the last cell is missing -> unexpected EOF.
        byte[] truncated = new byte[full.length - 4];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        assertThrows(IOException.class, () -> DynamicIntMatrix.deserializeFromBytes(truncated));
    }

    // ---------------------------------------------------------------
    // Task 4: CSV export with proper escaping
    // ---------------------------------------------------------------

    @Test
    public void testCsvSpecialCharacters() {
        String[] specials = {
                "plain",
                "has,comma",
                "has\"quote",
                "has\nnewline",
                "mix\",com\nma",
                "a,b,c",
                "\"quoted\"",
                ""
        };
        // Build a single CSV line from the escaped fields, then parse it back.
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < specials.length; i++) {
            if (i > 0) line.append(',');
            line.append(MatrixUtils.escapeCsv(specials[i]));
        }
        String[] parsed = MatrixUtils.parseCsvFields(line.toString());
        assertArrayEquals(specials, parsed,
                "escaped CSV fields must round-trip exactly");
    }

    @Test
    public void testCsvRoundTrip() throws Exception {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.addRow(new int[]{-4, 5, 600});
        m.addRow(new int[]{7, 0, 9});
        Path tmp = Files.createTempFile("matrix_csv", ".csv");
        try {
            MatrixUtils.exportToCsv(m, tmp);
            DynamicIntMatrix imported = MatrixUtils.importFromCsv(tmp);
            assertEquals(m.rows(), imported.rows());
            assertEquals(m.cols(), imported.cols());
            assertArrayEquals(m.flattenRowMajor(), imported.flattenRowMajor());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testCsvMalformedThrows() throws Exception {
        Path tmp = Files.createTempFile("matrix_bad", ".csv");
        try {
            // Missing header line
            Files.writeString(tmp, "1,2,3\n4,5,6\n");
            assertThrows(IOException.class, () -> MatrixUtils.importFromCsv(tmp));

            // Header present but wrong field count
            Files.writeString(tmp, "# rows=1, cols=2\n1,2,3\n");
            assertThrows(IOException.class, () -> MatrixUtils.importFromCsv(tmp));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ---------------------------------------------------------------
    // Task 5.5: Memory-leak verification via reflection
    // ---------------------------------------------------------------

    @Test
    public void testMemoryLeakReflection() throws Exception {
        DynamicIntMatrix m = new DynamicIntMatrix();
        for (int r = 0; r < 100; r++) {
            int[] row = new int[3];
            for (int c = 0; c < 3; c++) row[c] = r * 10 + c;
            m.addRow(row);
        }
        assertEquals(100, m.rows());

        // Remove 50 rows from the front (exercises the shift + nullification path).
        for (int i = 0; i < 50; i++) {
            m.removeRow(0);
        }
        assertEquals(50, m.rows());

        int[][] internal = extractData(m);
        for (int i = 50; i < 100; i++) {
            assertNull(internal[i], "slot " + i + " must be null (anti-fragmentation)");
        }
        for (int i = 0; i < 50; i++) {
            assertNotNull(internal[i], "slot " + i + " must still hold data");
        }
    }

    // ---------------------------------------------------------------
    // Reflection helper
    // ---------------------------------------------------------------

    private static int[][] extractData(DynamicIntMatrix m) throws Exception {
        Field f = DynamicIntMatrix.class.getDeclaredField("data");
        f.setAccessible(true);
        return (int[][]) f.get(m);
    }
}

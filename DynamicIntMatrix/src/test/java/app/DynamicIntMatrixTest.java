package app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DynamicIntMatrix covering core operations and edge cases.
 */
public class DynamicIntMatrixTest {

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
        // add column at end
        m.addCol(new int[]{100, 200});
        assertEquals(2, m.rows());
        assertEquals(3, m.cols());
        int[] flat = m.flattenRowMajor();
        assertArrayEquals(new int[]{10, 11, 100, 20, 21, 200}, flat);
    }

    @Test
    public void testInsertRowAndRemoveRow() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 1});
        m.addRow(new int[]{3, 3});
        m.addRow(1, new int[]{2, 2}); // insert in middle
        assertEquals(3, m.rows());
        assertArrayEquals(new int[]{1, 1}, m.removeRow(0)); // remove first
        assertEquals(2, m.rows());
        assertEquals(2, m.get(0, 0));
    }

    @Test
    public void testInsertColAndRemoveCol() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        m.addCol(1, new int[]{9, 8}); // insert column at index 1
        assertEquals(3, m.cols());
        assertArrayEquals(new int[]{2, 4}, m.removeCol(2)); // remove last column
        assertEquals(2, m.cols());
        assertEquals(9, m.get(0, 1));
        assertEquals(8, m.get(1, 1));
    }

    @Test
    public void testSubMatrix() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.addRow(new int[]{4, 5, 6});
        m.addRow(new int[]{7, 8, 9});
        DynamicIntMatrix sub = m.subMatrix(0, 2, 1, 3); // rows [0,2), cols [1,3)
        assertEquals(2, sub.rows());
        assertEquals(2, sub.cols());
        assertArrayEquals(new int[]{2, 3, 5, 6}, sub.flattenRowMajor());
        // ensure copy (mutating original doesn't affect sub)
        m.set(0, 1, 99);
        assertEquals(2, sub.get(0, 0));
    }

    @Test
    public void testTransposeAndRotateSquare() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        DynamicIntMatrix t = m.transpose();
        assertEquals(2, t.rows());
        assertEquals(2, t.cols());
        assertArrayEquals(new int[]{1, 3, 2, 4}, t.flattenRowMajor());

        m.rotate90Clockwise(); // in-place for square
        assertArrayEquals(new int[]{3, 1, 4, 2}, m.flattenRowMajor());
    }

    @Test
    public void testRotateNonSquare() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.addRow(new int[]{4, 5, 6});
        // 2x3 -> rotate -> becomes 3x2
        m.rotate90Clockwise();
        assertEquals(3, m.rows());
        assertEquals(2, m.cols());
        // expected rotated matrix:
        // [4,1]
        // [5,2]
        // [6,3]
        assertArrayEquals(new int[]{4, 1, 5, 2, 6, 3}, m.flattenRowMajor());
    }

    @Test
    public void testClearAndFill() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{7, 8});
        m.addRow(new int[]{9, 10});
        m.fill(5);
        assertArrayEquals(new int[]{5,5,5,5}, m.flattenRowMajor());
        m.clear();
        assertEquals(0, m.rows());
        assertEquals(0, m.cols());
    }

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
    public void testEdgeCasesInvalidArgs() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        assertThrows(NullPointerException.class, () -> m.addRow(null));
        assertThrows(NullPointerException.class, () -> m.addCol(null));
        assertThrows(IndexOutOfBoundsException.class, () -> m.removeRow(0));
        assertThrows(IndexOutOfBoundsException.class, () -> m.removeCol(0));

        m.addRow(new int[]{1,2});
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(0, 2));
    }

    @Test
    public void stressMultipleAddsRemoves() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        // build 50x50 matrix by adding rows
        for (int r = 0; r < 50; r++) {
            int[] row = new int[50];
            Arrays.fill(row, r);
            m.addRow(row);
        }
        assertEquals(50, m.rows());
        assertEquals(50, m.cols());
        // remove some rows and cols
        for (int i = 0; i < 10; i++) {
            m.removeRow(0);
            m.removeCol(m.cols() - 1);
        }
        assertEquals(40, m.rows());
        assertEquals(40, m.cols());
    }

    @Test
    public void testResize() {
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2});
        m.addRow(new int[]{3, 4});
        // 2x2
        
        m.resize(3, 3);
        assertEquals(3, m.rows());
        assertEquals(3, m.cols());
        // Check padding with 0
        assertEquals(0, m.get(2, 2));
        assertEquals(1, m.get(0, 0));
        
        m.resize(1, 1);
        assertEquals(1, m.rows());
        assertEquals(1, m.cols());
        assertEquals(1, m.get(0, 0));
        
        // Resize to larger and check that old data is preserved and new is 0
        m.resize(2, 2);
        assertEquals(1, m.get(0, 0));
        assertEquals(0, m.get(0, 1));
        assertEquals(0, m.get(1, 1));
    }

    @Test
    public void testMathOperations() {
        DynamicIntMatrix a = new DynamicIntMatrix();
        a.addRow(new int[]{1, 2});
        a.addRow(new int[]{3, 4});

        DynamicIntMatrix b = new DynamicIntMatrix();
        b.addRow(new int[]{10, 20});
        b.addRow(new int[]{30, 40});

        // Plus
        DynamicIntMatrix sum = a.plus(b);
        assertArrayEquals(new int[]{11, 22, 33, 44}, sum.flattenRowMajor());

        // Minus
        DynamicIntMatrix diff = b.minus(a);
        assertArrayEquals(new int[]{9, 18, 27, 36}, diff.flattenRowMajor());

        // Scalar multiply
        DynamicIntMatrix scaled = a.multiply(10);
        assertArrayEquals(new int[]{10, 20, 30, 40}, scaled.flattenRowMajor());

        // Matrix multiply
        // A (2x2) * B (2x2)
        // [1 2] * [10 20] = [1*10+2*30  1*20+2*40] = [10+60  20+80] = [70 100]
        // [3 4]   [30 40]   [3*10+4*30  3*20+4*40] = [30+120 60+160] = [150 220]
        DynamicIntMatrix prod = a.multiply(b);
        assertArrayEquals(new int[]{70, 100, 150, 220}, prod.flattenRowMajor());
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
        // [1 2 3] * [1 2]
        //           [3 4]
        //           [5 6]
        // = [1*1+2*3+3*5  1*2+2*4+3*6] = [1+6+15  2+8+18] = [22 28]
        assertArrayEquals(new int[]{22, 28}, res.flattenRowMajor());
        
        assertThrows(IllegalArgumentException.class, () -> b.multiply(a)); // 3x2 * 1x3 -> 2!=1 mismatch
    }
}

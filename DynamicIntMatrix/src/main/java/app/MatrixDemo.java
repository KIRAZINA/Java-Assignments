package app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demonstrates the refined {@link DynamicIntMatrix} capabilities:
 *   - dynamic row/column resizing and capacity management
 *   - mathematical operations (add, subtract, multiply, scalar, transpose)
 *   - rotation (including non-square)
 *   - submatrix extraction
 *   - binary serialization (with null-row support)
 *   - CSV export / import
 */
public class MatrixDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Basic Operations ===");
        int[][] initialData = {
            {1, 2, 3},
            {4, 5, 6}
        };
        DynamicIntMatrix m = MatrixUtils.fromArray(initialData);
        System.out.println("Initial Matrix:\n" + m);

        m.addRow(new int[]{7, 8, 9});
        System.out.println("After addRow:\n" + m);
        m.addCol(new int[]{10, 20, 30});
        System.out.println("After addCol:\n" + m);

        System.out.println("Element at (0,0): " + m.get(0, 0));
        m.set(0, 0, 999);
        System.out.println("After set(0,0, 999):\n" + m);

        m.removeRow(0);
        System.out.println("After removeRow(0):\n" + m);
        m.removeCol(0);
        System.out.println("After removeCol(0):\n" + m);


        System.out.println("\n=== 2. Capacity Management ===");
        m.ensureCapacity(20, 20);
        m.trimToSize();
        System.out.println("After ensureCapacity(20,20) then trimToSize(), size still "
                + m.rows() + "x" + m.cols());


        System.out.println("\n=== 3. Math Operations (immutable) ===");
        DynamicIntMatrix a = new DynamicIntMatrix();
        a.addRow(new int[]{1, 2});
        a.addRow(new int[]{3, 4});
        System.out.println("Matrix A:\n" + a);

        DynamicIntMatrix b = new DynamicIntMatrix();
        b.addRow(new int[]{10, 20});
        b.addRow(new int[]{30, 40});
        System.out.println("Matrix B:\n" + b);

        System.out.println("A + B:\n" + a.add(b));
        System.out.println("B - A:\n" + b.subtract(a));
        System.out.println("A * 10 (scalar):\n" + a.multiplyByScalar(10));
        System.out.println("A * B (matrix):\n" + a.multiply(b));
        System.out.println("(A is unchanged after those ops)\n" + a);


        System.out.println("\n=== 4. Transpose & Rotation ===");
        DynamicIntMatrix t = a.transpose();
        System.out.println("Transpose of A:\n" + t);

        DynamicIntMatrix nonSquare = new DynamicIntMatrix();
        nonSquare.addRow(new int[]{1, 2, 3});
        nonSquare.addRow(new int[]{4, 5, 6});
        System.out.println("Non-square 2x3:\n" + nonSquare);
        DynamicIntMatrix rotated = new DynamicIntMatrix();
        rotated.addRow(new int[]{1, 2, 3});
        rotated.addRow(new int[]{4, 5, 6});
        rotated.rotate90Clockwise(); // mutates
        System.out.println("After rotate90Clockwise (now 3x2):\n" + rotated);


        System.out.println("\n=== 5. Submatrix ===");
        DynamicIntMatrix big = MatrixUtils.fromArray(new int[][]{
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9}
        });
        System.out.println("Original 3x3:\n" + big);
        DynamicIntMatrix sub = big.getSubmatrix(0, 1, 2, 3); // rows [0,2), cols [1,3)
        System.out.println("Submatrix getSubmatrix(0,1,2,3):\n" + sub);


        System.out.println("\n=== 6. Deep Copy & Equality ===");
        DynamicIntMatrix bCopy = MatrixUtils.copyOf(b);
        System.out.println("Deep Copy of B equals B? " + MatrixUtils.equals(b, bCopy));
        bCopy.set(0, 0, -1);
        System.out.println("After mutating copy, B unchanged? " + MatrixUtils.equals(b, bCopy));


        System.out.println("\n=== 7. Binary Serialization (with null rows) ===");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixIO.writeTo(b, baos);
        byte[] bytes = baos.toByteArray();
        System.out.println("Serialized B to " + bytes.length + " bytes.");
        DynamicIntMatrix restored = MatrixIO.readFrom(new ByteArrayInputStream(bytes));
        System.out.println("Restored Matrix:\n" + restored);
        System.out.println("Restored equals original? " + MatrixUtils.equals(b, restored));


        System.out.println("\n=== 8. CSV Export / Import ===");
        Path csv = Files.createTempFile("matrix_demo", ".csv");
        MatrixUtils.exportToCsv(b, csv);
        DynamicIntMatrix fromCsv = MatrixUtils.importFromCsv(csv);
        System.out.println("CSV content:\n" + new String(Files.readAllBytes(csv)));
        System.out.println("Imported from CSV equals original? " + MatrixUtils.equals(b, fromCsv));
        Files.deleteIfExists(csv);
    }
}

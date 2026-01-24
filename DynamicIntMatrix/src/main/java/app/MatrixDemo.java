package app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Comprehensive demo runner for DynamicIntMatrix.
 * Demonstrates all key functionality:
 * 1. Creation and Basic Operations (Add/Remove Row/Col, Get/Set)
 * 2. Resizing
 * 3. Math Operations (Plus, Minus, Scalar Multiply, Matrix Multiply)
 * 4. Transformations (Transpose, Rotate)
 * 5. Submatrix Extraction
 * 6. Utility Functions (CSV Export, Copy, Equality)
 * 7. Binary Serialization
 */
public class MatrixDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Basic Operations ===");
        // Create from array using Utils
        int[][] initialData = {
            {1, 2, 3},
            {4, 5, 6}
        };
        DynamicIntMatrix m = MatrixUtils.fromArray(initialData);
        System.out.println("Initial Matrix:\n" + m);

        // Add Row/Col
        m.addRow(new int[]{7, 8, 9});
        System.out.println("After addRow:\n" + m);
        m.addCol(new int[]{10, 20, 30});
        System.out.println("After addCol:\n" + m);

        // Set/Get
        System.out.println("Element at (0,0): " + m.get(0, 0));
        m.set(0, 0, 999);
        System.out.println("After set(0,0, 999):\n" + m);

        // Remove
        m.removeRow(0);
        System.out.println("After removeRow(0):\n" + m);
        m.removeCol(0);
        System.out.println("After removeCol(0):\n" + m);

        
        System.out.println("\n=== 2. Resizing ===");
        m.resize(4, 4);
        System.out.println("Resized to 4x4 (padding with 0):\n" + m);
        m.resize(2, 2);
        System.out.println("Resized to 2x2 (truncating):\n" + m);


        System.out.println("\n=== 3. Math Operations ===");
        DynamicIntMatrix a = new DynamicIntMatrix();
        a.addRow(new int[]{1, 2});
        a.addRow(new int[]{3, 4});
        System.out.println("Matrix A:\n" + a);

        DynamicIntMatrix b = new DynamicIntMatrix();
        b.addRow(new int[]{10, 20});
        b.addRow(new int[]{30, 40});
        System.out.println("Matrix B:\n" + b);

        System.out.println("A + B:\n" + a.plus(b));
        System.out.println("B - A:\n" + b.minus(a));
        System.out.println("A * 10 (Scalar):\n" + a.multiply(10));
        System.out.println("A * B (Matrix):\n" + a.multiply(b));


        System.out.println("\n=== 4. Transformations ===");
        DynamicIntMatrix t = a.transpose();
        System.out.println("Transpose of A:\n" + t);
        
        System.out.println("Rotate A 90 deg clockwise (mutates A):");
        a.rotate90Clockwise();
        System.out.println(a);


        System.out.println("\n=== 5. Submatrix ===");
        // Create 3x3 for submatrix
        DynamicIntMatrix big = MatrixUtils.fromArray(new int[][]{
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9}
        });
        System.out.println("Original 3x3:\n" + big);
        // Extract center 2x2
        DynamicIntMatrix sub = big.subMatrix(0, 2, 1, 3);
        System.out.println("Submatrix [0,2) rows, [1,3) cols:\n" + sub);


        System.out.println("\n=== 6. Utilities ===");
        System.out.println("CSV Export of B:\n" + MatrixUtils.toCsv(b));
        
        DynamicIntMatrix bCopy = MatrixUtils.copyOf(b);
        System.out.println("Deep Copy of B equals B? " + MatrixUtils.equals(b, bCopy));


        System.out.println("\n=== 7. Serialization ===");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixIO.writeTo(b, baos);
        byte[] bytes = baos.toByteArray();
        System.out.println("Serialized B to " + bytes.length + " bytes.");
        
        DynamicIntMatrix restored = MatrixIO.readFrom(new ByteArrayInputStream(bytes));
        System.out.println("Restored Matrix:\n" + restored);
        System.out.println("Restored equals original? " + MatrixUtils.equals(b, restored));
    }
}

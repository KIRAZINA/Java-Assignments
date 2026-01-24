package app;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility helpers for DynamicIntMatrix used in tests and demos.
 *
 * Contains comparison, copy and CSV export helpers that do not rely on
 * java.util collections for matrix content handling.
 */
public final class MatrixUtils {

    private MatrixUtils() { }

    /**
     * Compare two matrices for equality of dimensions and cell values.
     *
     * @param a first matrix (may be null)
     * @param b second matrix (may be null)
     * @return true if both are null or both have same rows, cols and identical values
     */
    public static boolean equals(DynamicIntMatrix a, DynamicIntMatrix b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.rows() != b.rows() || a.cols() != b.cols()) return false;
        int rows = a.rows();
        int cols = a.cols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (a.get(r, c) != b.get(r, c)) return false;
            }
        }
        return true;
    }

    /**
     * Create a deep copy of the given matrix.
     *
     * @param src source matrix (must not be null)
     * @return new DynamicIntMatrix with copied contents
     */
    public static DynamicIntMatrix copyOf(DynamicIntMatrix src) {
        Objects.requireNonNull(src, "src must not be null");
        DynamicIntMatrix copy = new DynamicIntMatrix(Math.max(src.rows(), 1), Math.max(src.cols(), 1));
        // if empty, return empty matrix
        if (src.rows() == 0 || src.cols() == 0) {
            return copy;
        }
        for (int r = 0; r < src.rows(); r++) {
            int[] row = new int[src.cols()];
            for (int c = 0; c < src.cols(); c++) {
                row[c] = src.get(r, c);
            }
            copy.addRow(row);
        }
        return copy;
    }

    /**
     * Produce a CSV string (row-major) for quick debugging or small exports.
     * Each row is on its own line, values separated by commas.
     *
     * @param m matrix to convert (must not be null)
     * @return CSV representation (empty string for empty matrix)
     */
    public static String toCsv(DynamicIntMatrix m) {
        Objects.requireNonNull(m, "matrix must not be null");
        if (m.rows() == 0 || m.cols() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m.rows(); r++) {
            for (int c = 0; c < m.cols(); c++) {
                sb.append(m.get(r, c));
                if (c + 1 < m.cols()) sb.append(',');
            }
            if (r + 1 < m.rows()) sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Build a matrix from a 2D primitive int array.
     *
     * @param arr 2D int array (must not be null, rows must be non-null and same length)
     * @return DynamicIntMatrix containing the same values
     */
    public static DynamicIntMatrix fromArray(int[][] arr) {
        Objects.requireNonNull(arr, "input array must not be null");
        if (arr.length == 0) {
            return new DynamicIntMatrix();
        }
        int rlen = arr.length;
        int clen = arr[0].length;
        DynamicIntMatrix m = new DynamicIntMatrix(Math.max(rlen, 1), Math.max(clen, 1));
        for (int r = 0; r < rlen; r++) {
            if (arr[r] == null) throw new NullPointerException("row " + r + " is null");
            if (arr[r].length != clen) throw new IllegalArgumentException("inconsistent row length at row " + r);
            m.addRow(Arrays.copyOf(arr[r], arr[r].length));
        }
        return m;
    }
}

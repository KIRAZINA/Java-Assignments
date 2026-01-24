package app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Dynamic two-dimensional integer matrix.
 *
 * Storage: int[][] data, actual sizes rows and cols.
 * Capacity: data.length and data[r].length may be >= rows/cols.
 *
 * This class provides dynamic growth for rows and columns and basic
 * operations including indexed insertion and removal, submatrix,
 * transpose and rotation.
 */
public class DynamicIntMatrix {
    private int[][] data;
    private int rows;
    private int cols;

    private static final int INITIAL_ROWS = 4;
    private static final int INITIAL_COLS = 4;
    private static final int GROW_FACTOR = 2;

    public DynamicIntMatrix() {
        this(INITIAL_ROWS, INITIAL_COLS);
        this.rows = 0;
        this.cols = 0;
    }

    public DynamicIntMatrix(int initialRows, int initialCols) {
        if (initialRows <= 0 || initialCols <= 0) {
            throw new IllegalArgumentException("initial dimensions must be > 0");
        }
        data = new int[initialRows][initialCols];
        // initialize each row array
        for (int i = 0; i < initialRows; i++) {
            data[i] = new int[initialCols];
        }
        rows = 0;
        cols = 0;
    }

    // Returns actual number of rows
    public int rows() {
        return rows;
    }

    // Returns actual number of columns
    public int cols() {
        return cols;
    }

    public boolean isEmpty() {
        return rows == 0 || cols == 0;
    }

    private void ensureRowCapacity(int minRows) {
        if (data.length >= minRows) return;
        int newCapacity = Math.max(minRows, data.length * GROW_FACTOR);
        int[][] newData = new int[newCapacity][];
        System.arraycopy(data, 0, newData, 0, data.length);
        int baseCols = (data.length > 0 && data[0] != null) ? data[0].length : Math.max(INITIAL_COLS, cols);
        for (int i = data.length; i < newCapacity; i++) {
            newData[i] = new int[baseCols];
        }
        data = newData;
    }

    private void ensureColCapacity(int minCols) {
        int currentRowCapacity = data.length > 0 ? data[0].length : 0;
        if (currentRowCapacity >= minCols) return;
        int newColCapacity = Math.max(minCols, Math.max(currentRowCapacity * GROW_FACTOR, INITIAL_COLS));
        for (int r = 0; r < data.length; r++) {
            int[] oldRow = data[r];
            int[] newRow = new int[newColCapacity];
            if (oldRow != null) {
                System.arraycopy(oldRow, 0, newRow, 0, oldRow.length);
            }
            data[r] = newRow;
        }
    }

    private void checkIndex(int row, int col) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("row out of bounds: " + row);
        }
        if (col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException("col out of bounds: " + col);
        }
    }

    public int get(int row, int col) {
        checkIndex(row, col);
        return data[row][col];
    }

    public int set(int row, int col, int value) {
        checkIndex(row, col);
        int old = data[row][col];
        data[row][col] = value;
        return old;
    }

    /**
     * Append a row at the end. If matrix is empty, cols is set to rowValues.length.
     */
    public void addRow(int[] rowValues) {
        Objects.requireNonNull(rowValues, "rowValues must not be null");
        if (rows == 0 && cols == 0) {
            cols = rowValues.length;
            ensureRowCapacity(Math.max(1, rows + 1));
            ensureColCapacity(cols);
        } else {
            if (rowValues.length != cols) {
                throw new IllegalArgumentException("row length must equal current cols: expected " + cols + " but was " + rowValues.length);
            }
        }
        ensureRowCapacity(rows + 1);
        ensureColCapacity(cols);
        System.arraycopy(rowValues, 0, data[rows], 0, cols);
        rows++;
    }

    /**
     * Insert a row at the given index. Valid indices: 0..rows (inclusive).
     */
    public void addRow(int index, int[] rowValues) {
        Objects.requireNonNull(rowValues, "rowValues must not be null");
        if (index < 0 || index > rows) {
            throw new IndexOutOfBoundsException("row index out of bounds: " + index);
        }
        if (rows == 0 && cols == 0) {
            // first insertion defines cols
            cols = rowValues.length;
            ensureRowCapacity(Math.max(1, rows + 1));
            ensureColCapacity(cols);
        } else {
            if (rowValues.length != cols) {
                throw new IllegalArgumentException("row length must equal current cols: expected " + cols + " but was " + rowValues.length);
            }
        }
        ensureRowCapacity(rows + 1);
        ensureColCapacity(cols);
        // shift rows down starting from index
        if (rows - index > 0) {
            System.arraycopy(data, index, data, index + 1, rows - index);
        }
        // copy new row into position
        System.arraycopy(rowValues, 0, data[index], 0, cols);
        rows++;
    }

    /**
     * Remove row at index and return removed row values.
     */
    public int[] removeRow(int index) {
        if (index < 0 || index >= rows) {
            throw new IndexOutOfBoundsException("row index out of bounds: " + index);
        }
        int[] removed = new int[cols];
        // copy removed values before shifting
        System.arraycopy(data[index], 0, removed, 0, cols);

        // shift rows up (move references)
        if (rows - index - 1 > 0) {
            System.arraycopy(data, index + 1, data, index, rows - index - 1);
        }

        // replace the last occupied row slot with a fresh array to avoid
        // clearing an array that is now referenced from another position
        int last = rows - 1;
        int rowCapacity = (data.length > 0 && data[0] != null) ? data[0].length : Math.max(INITIAL_COLS, cols);
        data[last] = new int[rowCapacity];

        rows--;
        // If matrix becomes empty, reset cols to 0 (keep capacity)
        if (rows == 0) {
            cols = 0;
        }
        return removed;
    }


    /**
     * Append a column at the end. If matrix is empty, rows is set to colValues.length.
     */
    public void addCol(int[] colValues) {
        Objects.requireNonNull(colValues, "colValues must not be null");
        if (rows == 0 && cols == 0) {
            rows = colValues.length;
            ensureRowCapacity(rows);
            cols = 1;
            ensureColCapacity(cols);
        } else {
            if (colValues.length != rows) {
                throw new IllegalArgumentException("col length must equal current rows: expected " + rows + " but was " + colValues.length);
            }
        }
        ensureColCapacity(cols + 1);
        for (int r = 0; r < rows; r++) {
            data[r][cols] = colValues[r];
        }
        cols++;
    }

    /**
     * Insert a column at the given index. Valid indices: 0..cols (inclusive).
     */
    public void addCol(int index, int[] colValues) {
        Objects.requireNonNull(colValues, "colValues must not be null");
        if (index < 0 || index > cols) {
            throw new IndexOutOfBoundsException("col index out of bounds: " + index);
        }
        if (rows == 0 && cols == 0) {
            // first insertion defines rows
            rows = colValues.length;
            ensureRowCapacity(rows);
            cols = 0; // will be incremented below
            ensureColCapacity(Math.max(1, cols + 1));
        } else {
            if (colValues.length != rows) {
                throw new IllegalArgumentException("col length must equal current rows: expected " + rows + " but was " + colValues.length);
            }
        }
        ensureColCapacity(cols + 1);
        // for each row, shift elements right from index to cols-1
        for (int r = 0; r < rows; r++) {
            int[] row = data[r];
            if (cols - index > 0) {
                System.arraycopy(row, index, row, index + 1, cols - index);
            }
            row[index] = colValues[r];
        }
        cols++;
    }

    /**
     * Remove column at index and return removed column values.
     */
    public int[] removeCol(int index) {
        if (index < 0 || index >= cols) {
            throw new IndexOutOfBoundsException("col index out of bounds: " + index);
        }
        int[] removed = new int[rows];
        for (int r = 0; r < rows; r++) {
            removed[r] = data[r][index];
            int[] row = data[r];
            if (cols - index - 1 > 0) {
                System.arraycopy(row, index + 1, row, index, cols - index - 1);
            }
            // clear last occupied column in this row
            row[cols - 1] = 0;
        }
        cols--;
        if (cols == 0) {
            // if no columns left, reset rows to 0 as well (matrix becomes empty)
            rows = 0;
        }
        return removed;
    }

    public void clear() {
        for (int r = 0; r < rows; r++) {
            int[] row = data[r];
            if (row != null) {
                for (int c = 0; c < cols; c++) {
                    row[c] = 0;
                }
            }
        }
        rows = 0;
        cols = 0;
    }

    public void fill(int value) {
        for (int r = 0; r < rows; r++) {
            int[] row = data[r];
            for (int c = 0; c < cols; c++) {
                row[c] = value;
            }
        }
    }

    public int[] flattenRowMajor() {
        int total = rows * cols;
        int[] out = new int[total];
        int pos = 0;
        for (int r = 0; r < rows; r++) {
            System.arraycopy(data[r], 0, out, pos, cols);
            pos += cols;
        }
        return out;
    }

    /**
     * Write matrix to DataOutputStream in binary format:
     * int rows, int cols, then rows*cols ints in row-major order.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(rows);
        out.writeInt(cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out.writeInt(data[r][c]);
            }
        }
    }

    /**
     * Read matrix from InputStream in binary format.
     * Does not close the stream.
     */
    public static DynamicIntMatrix readFrom(InputStream in) throws IOException {
        DataInputStream dis;
        if (in instanceof DataInputStream) {
            dis = (DataInputStream) in;
        } else {
            dis = new DataInputStream(in);
        }

        int rows = dis.readInt();
        int cols = dis.readInt();

        DynamicIntMatrix m = new DynamicIntMatrix(Math.max(rows, 1), Math.max(cols, 1));
        
        if (rows > 0 && cols > 0) {
            m.rows = rows;
            m.cols = cols;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    m.data[r][c] = dis.readInt();
                }
            }
        }
        return m;
    }


    /**
     * Return a submatrix defined by half-open ranges:
     * [rowFrom, rowTo) and [colFrom, colTo).
     *
     * Preconditions:
     *  - 0 <= rowFrom <= rowTo <= rows
     *  - 0 <= colFrom <= colTo <= cols
     *
     * The returned matrix is a copy (no shared backing).
     */
    public DynamicIntMatrix subMatrix(int rowFrom, int rowTo, int colFrom, int colTo) {
        if (rowFrom < 0 || rowTo < rowFrom || rowTo > rows) {
            throw new IndexOutOfBoundsException("invalid row range: [" + rowFrom + ", " + rowTo + ")");
        }
        if (colFrom < 0 || colTo < colFrom || colTo > cols) {
            throw new IndexOutOfBoundsException("invalid col range: [" + colFrom + ", " + colTo + ")");
        }
        int newRows = rowTo - rowFrom;
        int newCols = colTo - colFrom;
        
        // Create with exact capacity needed
        DynamicIntMatrix result = new DynamicIntMatrix(Math.max(newRows, 1), Math.max(newCols, 1));
        
        // if empty submatrix, return empty matrix (rows/cols remain 0)
        if (newRows == 0 || newCols == 0) {
            return result;
        }

        // Optimization: directly copy into result's data to avoid double allocation/copying
        // Since we are in the same class, we can access private members of 'result'
        result.rows = newRows;
        result.cols = newCols;
        
        for (int r = 0; r < newRows; r++) {
            // result.data[r] was allocated by constructor
            System.arraycopy(this.data[rowFrom + r], colFrom, result.data[r], 0, newCols);
        }
        return result;
    }

    /**
     * Return a new matrix that is the transpose of this matrix.
     * The original matrix is not modified.
     */
    public DynamicIntMatrix transpose() {
        if (rows == 0 || cols == 0) {
            return new DynamicIntMatrix();
        }
        DynamicIntMatrix t = new DynamicIntMatrix(Math.max(cols, 1), Math.max(rows, 1));
        // build transposed rows
        for (int r = 0; r < cols; r++) {
            int[] newRow = new int[rows];
            for (int c = 0; c < rows; c++) {
                newRow[c] = this.data[c][r];
            }
            t.addRow(newRow);
        }
        return t;
    }

    /**
     * Rotate the matrix 90 degrees clockwise in-place when possible.
     *
     * Behavior:
     *  - If the matrix is square (rows == cols), perform an in-place rotation
     *    using layer-by-layer swaps.
     *  - If the matrix is non-square, create a new backing array with swapped
     *    dimensions and replace internal storage so that this instance becomes
     *    the rotated matrix (method mutates this object).
     *
     * Complexity: O(rows*cols).
     */
    public void rotate90Clockwise() {
        if (rows == 0 || cols == 0) {
            return; // nothing to do
        }
        if (rows == cols) {
            // in-place rotation for square matrix
            int n = rows;
            for (int layer = 0; layer < n / 2; layer++) {
                int first = layer;
                int last = n - 1 - layer;
                for (int i = first; i < last; i++) {
                    int offset = i - first;
                    int top = data[first][i]; // save top
                    // left -> top
                    data[first][i] = data[last - offset][first];
                    // bottom -> left
                    data[last - offset][first] = data[last][last - offset];
                    // right -> bottom
                    data[last][last - offset] = data[i][last];
                    // top -> right
                    data[i][last] = top;
                }
            }
            // rows and cols unchanged
        } else {
            // non-square: create new backing with swapped dimensions
            int newRows = cols;
            int newCols = rows;
            int[][] newData = new int[Math.max(newRows, 1)][Math.max(newCols, 1)];
            // initialize rows
            for (int r = 0; r < newData.length; r++) {
                newData[r] = new int[Math.max(newCols, 1)];
            }
            // fill newData with rotated values
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    // position (r,c) -> (c, newCols - 1 - r)
                    newData[c][newCols - 1 - r] = data[r][c];
                }
            }
            // replace internal storage and update sizes
            this.data = newData;
            this.rows = newRows;
            this.cols = newCols;
        }
    }

    /**
     * Resize the matrix to the new dimensions.
     * If new dimensions are larger, new cells are filled with 0.
     * If new dimensions are smaller, the matrix is truncated.
     *
     * Complexity: O(newRows * newCols) or O(rows * cols) depending on growth/shrinkage.
     */
    public void resize(int newRows, int newCols) {
        if (newRows < 0 || newCols < 0) {
            throw new IllegalArgumentException("dimensions must be non-negative");
        }
        if (newRows == 0 || newCols == 0) {
            clear();
            return;
        }

        // If expanding rows, ensure capacity
        if (newRows > rows) {
            ensureRowCapacity(newRows);
        }
        
        // If expanding columns, ensure capacity for all current rows
        // (Note: ensureColCapacity iterates over 'data.length', covering potential new rows too if they were allocated)
        if (newCols > cols) {
            ensureColCapacity(newCols);
        }

        // If shrinking rows, we just update the 'rows' counter.
        // But for memory hygiene we might want to nullify or clear removed rows if the drop is significant?
        // The current implementation of removeRow clears specific slots.
        // For resize, we will just update the counter 'rows' and 'cols'.
        // However, if we shrink columns, we don't necessarily reallocate rows, just update 'cols'.
        // BUT: if we later expand, old data might be there?
        // Let's ensure that if we expand back, the "new" cells are 0.
        // If we shrink, we don't need to zero out immediately unless we want to avoid memory leaks (but these are ints).
        // Since they are primitive ints, no memory leak reference issues.
        
        // However, the contract usually implies "new cells are 0".
        // If we shrink then grow, the "re-grown" cells should be 0.
        // So if we shrink, we should probably zero out the "dead" area or handle it on growth.
        // 'ensureColCapacity' copies data.
        
        // Implementation strategy:
        // 1. If changing cols:
        //    - If growing: existing rows need to be expanded (handled by ensureColCapacity). New cells are 0 by default Java array init.
        //    - If shrinking: we just reduce 'cols'. But we should zero out the "tail" to be safe? 
        //      Actually, clear() zeroes everything. removeRow() zeroes.
        //      Let's zero out the truncated parts for safety.
        
        if (newCols < cols) {
            for (int r = 0; r < rows; r++) {
                if (data[r] != null) {
                    Arrays.fill(data[r], newCols, cols, 0);
                }
            }
        }
        
        if (newRows < rows) {
            for (int r = newRows; r < rows; r++) {
                if (data[r] != null) {
                    // We can either null the row or fill with 0.
                    // removeRow keeps the array object but fills with 0 if it's reused?
                    // actually removeRow allocates a new empty array for the last slot.
                    // Here we can just fill with 0 to be safe.
                    Arrays.fill(data[r], 0);
                }
            }
        }
        
        // Now update dimensions
        // Note: if we grew rows, the new rows are already allocated by ensureRowCapacity and are 0-filled by default.
        // If we grew cols, ensureColCapacity handled reallocation and copy, new slots are 0.
        
        this.rows = newRows;
        this.cols = newCols;
    }

    /**
     * Add another matrix to this one and return the result.
     * C = A + B
     * Dimensions must match.
     */
    public DynamicIntMatrix plus(DynamicIntMatrix other) {
        Objects.requireNonNull(other);
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException("Matrix dimensions must match for addition: " +
                    rows + "x" + cols + " vs " + other.rows + "x" + other.cols);
        }
        DynamicIntMatrix result = new DynamicIntMatrix(rows, cols);
        result.rows = rows;
        result.cols = cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result.data[r][c] = this.data[r][c] + other.data[r][c];
            }
        }
        return result;
    }

    /**
     * Subtract another matrix from this one and return the result.
     * C = A - B
     * Dimensions must match.
     */
    public DynamicIntMatrix minus(DynamicIntMatrix other) {
        Objects.requireNonNull(other);
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException("Matrix dimensions must match for subtraction: " +
                    rows + "x" + cols + " vs " + other.rows + "x" + other.cols);
        }
        DynamicIntMatrix result = new DynamicIntMatrix(rows, cols);
        result.rows = rows;
        result.cols = cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result.data[r][c] = this.data[r][c] - other.data[r][c];
            }
        }
        return result;
    }

    /**
     * Multiply this matrix by a scalar.
     * C = A * s
     */
    public DynamicIntMatrix multiply(int scalar) {
        DynamicIntMatrix result = new DynamicIntMatrix(rows, cols);
        if (rows == 0 || cols == 0) return result;
        
        result.rows = rows;
        result.cols = cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result.data[r][c] = this.data[r][c] * scalar;
            }
        }
        return result;
    }

    /**
     * Multiply this matrix by another matrix.
     * C = A * B
     * A.cols must equal B.rows.
     * Result size: A.rows x B.cols.
     * Complexity: O(A.rows * B.cols * A.cols).
     */
    public DynamicIntMatrix multiply(DynamicIntMatrix other) {
        Objects.requireNonNull(other);
        if (this.cols != other.rows) {
            throw new IllegalArgumentException("Matrix multiplication undefined: cols " +
                    this.cols + " != rows " + other.rows);
        }
        int r1 = this.rows;
        int c1 = this.cols; // same as r2
        int c2 = other.cols;
        
        DynamicIntMatrix result = new DynamicIntMatrix(Math.max(r1, 1), Math.max(c2, 1));
        if (r1 == 0 || c1 == 0 || c2 == 0) {
            // Result is technically r1 x c2, but if any dimension is 0, it's empty.
            // If r1=0, result is 0 rows.
            // If c2=0, result is 0 cols.
            // If c1=0 (inner dim), sum is over empty range -> 0.
            if (r1 > 0 && c2 > 0) {
                result.rows = r1;
                result.cols = c2;
                // initialized to 0s, which is correct for sum of empty range
            }
            return result;
        }

        result.rows = r1;
        result.cols = c2;

        // Naive O(N^3) multiplication
        // Optimization: Cache row of A and iterate B?
        // Or just standard loop order (i, k, j) or (i, j, k).
        // (i, k, j) is usually cache-friendlier for row-major arrays in Java.
        // i: row in A (and Result)
        // k: col in A, row in B
        // j: col in B (and Result)
        
        for (int i = 0; i < r1; i++) {
            int[] rowA = this.data[i];
            int[] rowRes = result.data[i];
            for (int k = 0; k < c1; k++) {
                int valA = rowA[k];
                if (valA == 0) continue; // optimization for sparse-ish matrices
                int[] rowB = other.data[k];
                for (int j = 0; j < c2; j++) {
                    rowRes[j] += valA * rowB[j];
                }
            }
        }
        
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DynamicIntMatrix[").append(rows).append("x").append(cols).append("]\n");
        for (int r = 0; r < rows; r++) {
            sb.append("[");
            for (int c = 0; c < cols; c++) {
                sb.append(data[r][c]);
                if (c + 1 < cols) sb.append(", ");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }
}

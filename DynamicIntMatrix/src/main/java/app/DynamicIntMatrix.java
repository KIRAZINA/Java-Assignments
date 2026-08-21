package app;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Dynamic two-dimensional integer matrix built entirely from raw {@code int[][]}
 * storage. This implementation deliberately avoids {@code java.util.Arrays.copyOf},
 * {@code java.util.List} and streams for every core operation; the ONLY copying
 * primitive used is {@link System#arraycopy}.
 *
 * <h2>Memory model</h2>
 * Storage is an outer array {@code int[][] data} representing the row capacity
 * (i.e. {@code data.length >= rows}). Each slot {@code data[r]} is itself an
 * {@code int[]} of length {@code colCapacity} (i.e. {@code data[r].length >= cols}).
 * The logical dimensions are {@code rows} and {@code cols}.
 *
 * Keeping a separate {@code colCapacity} field (rather than re-deriving it from
 * {@code data[0].length}) lets us:
 *   - allocate brand new row slots after a removed row was nullified (see
 *     {@link #removeRow}), and
 *   - keep every allocated row the same width so {@code System.arraycopy} across
 *     rows is always safe.
 *
 * <h2>Anti-fragmentation</h2>
 * When a row is removed its reference is set to {@code null} instead of being
 * left as a dangling/duplicate reference. This lets the garbage collector
 * reclaim the backing {@code int[]} immediately. Slots beyond the logical row
 * count that are no longer needed are also nullified. {@link #trimToSize()}
 * shrinks the outer array to exactly {@code rows} entries.
 *
 * <h2>Immutability contract</h2>
 * Every mathematical operation ({@code add}, {@code subtract}, {@code multiply},
 * {@code multiplyByScalar}, {@code transpose}, {@code getSubmatrix},
 * {@code deepCopy}) returns a NEW {@code DynamicIntMatrix}. Mutating operations
 * are limited to the insertion/removal/resize family and {@code rotate90Clockwise}.
 */
public class DynamicIntMatrix {

    /** Outer array; its length is the ROW CAPACITY. May contain null row slots. */
    private int[][] data;
    /** Logical number of rows actually holding data. */
    private int rows;
    /** Logical number of columns actually holding data. */
    private int cols;
    /** Per-row array capacity; every allocated row has this length. */
    private int colCapacity;

    private static final int INITIAL_ROWS = 4;
    private static final int INITIAL_COLS = 4;
    private static final int GROW_FACTOR = 2;

    /**
     * Construct an empty matrix (zero logical dimensions, zero capacity).
     * Capacity is allocated lazily as rows/columns are added, which keeps the
     * footprint of an empty matrix minimal.
     */
    public DynamicIntMatrix() {
        this.data = new int[0][];
        this.rows = 0;
        this.cols = 0;
        this.colCapacity = 0;
    }

    /**
     * Construct a matrix with the given CAPACITY (not logical size). The object
     * is allocated to hold {@code initialRows * initialCols} cells, but its
     * logical size starts at {@code 0 x 0} until content is added. Used
     * internally as a pre-allocation strategy to avoid repeated re-growth.
     */
    public DynamicIntMatrix(int initialRows, int initialCols) {
        if (initialRows < 0 || initialCols < 0) {
            throw new IllegalArgumentException("initial dimensions must be non-negative");
        }
        this.data = new int[initialRows][];
        this.colCapacity = initialCols;
        for (int i = 0; i < initialRows; i++) {
            // Allocate every row at the requested column capacity up front.
            this.data[i] = new int[initialCols];
        }
        this.rows = 0;
        this.cols = 0;
    }

    /** @return logical number of rows. */
    public int rows() {
        return rows;
    }

    /** @return logical number of columns. */
    public int cols() {
        return cols;
    }

    /** @return true if the matrix has no cells (either dimension is zero). */
    public boolean isEmpty() {
        return rows == 0 || cols == 0;
    }

    /**
     * Ensure the outer array can hold at least {@code minRows} rows.
     * Grows geometrically (doubling) to amortise the cost of repeated appends.
     * Newly created outer slots are filled with fresh {@code int[colCapacity]}
     * arrays so that they are never {@code null} when first touched.
     */
    private void ensureRowCapacity(int minRows) {
        if (minRows <= 0) return;
        if (data.length >= minRows) return;
        int newCap = Math.max(minRows, data.length == 0 ? INITIAL_ROWS : data.length * GROW_FACTOR);
        int[][] newData = new int[newCap][];
        // Copy existing references (may include nulls) directly; this is a pure
        // reference move of the OUTER array, no per-element work on the rows.
        System.arraycopy(data, 0, newData, 0, data.length);
        int cc = Math.max(colCapacity, INITIAL_COLS);
        for (int i = data.length; i < newCap; i++) {
            newData[i] = new int[cc];
        }
        data = newData;
    }

    /**
     * Ensure every allocated row array can hold at least {@code minCols} columns.
     * Because column growth requires widening each row individually we iterate
     * the rows and use {@link System#arraycopy} to preserve existing values.
     * Freed (null) slots are skipped here and allocated lazily on reuse.
     */
    private void ensureColCapacity(int minCols) {
        if (minCols <= 0) return;
        if (colCapacity >= minCols) return;
        int newCap = Math.max(minCols,
                Math.max(colCapacity == 0 ? INITIAL_COLS : colCapacity * GROW_FACTOR, INITIAL_COLS));
        for (int r = 0; r < data.length; r++) {
            if (data[r] == null) continue; // will be (re)allocated on reuse
            int[] old = data[r];
            int[] grown = new int[newCap];
            System.arraycopy(old, 0, grown, 0, old.length);
            data[r] = grown;
        }
        colCapacity = newCap;
    }

    /**
     * Guarantee that {@code data[index]} is non-null and wide enough to receive
     * data. Used before writing into a slot that may have been nullified by a
     * previous {@link #removeRow}.
     */
    private void ensureRowReady(int index) {
        ensureRowCapacity(index + 1);
        if (data[index] == null) {
            data[index] = new int[Math.max(colCapacity, INITIAL_COLS)];
        }
    }

    private void checkIndex(int row, int col) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("row out of bounds: " + row + " (rows=" + rows + ")");
        }
        if (col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException("col out of bounds: " + col + " (cols=" + cols + ")");
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
     * Append a row at the end. If the matrix is empty this first insertion
     * defines {@code cols}.
     *
     * @throws NullPointerException if {@code rowValues} is null
     * @throws IllegalArgumentException if {@code rowValues.length != cols}
     */
    public void addRow(int[] rowValues) {
        Objects.requireNonNull(rowValues, "rowValues must not be null");
        if (rows == 0 && cols == 0) {
            cols = rowValues.length;
        } else if (rowValues.length != cols) {
            throw new IllegalArgumentException("row length must equal current cols: expected "
                    + cols + " but was " + rowValues.length);
        }
        ensureColCapacity(cols);
        ensureRowCapacity(rows + 1);
        ensureRowReady(rows);
        System.arraycopy(rowValues, 0, data[rows], 0, cols);
        rows++;
    }

    /**
     * Insert a row at {@code index} (valid range {@code 0..rows} inclusive).
     * Rows are shifted DOWN by one using {@link System#arraycopy} on the outer
     * reference array. The destination slot at {@code index} is given a FRESH
     * array, because the in-place shift leaves {@code data[index]} aliased to the
     * shifted element at {@code index+1}; reusing it would corrupt that neighbor.
     */
    public void addRow(int index, int[] rowValues) {
        Objects.requireNonNull(rowValues, "rowValues must not be null");
        if (index < 0 || index > rows) {
            throw new IndexOutOfBoundsException("row index out of bounds: " + index);
        }
        if (rows == 0 && cols == 0) {
            cols = rowValues.length;
        } else if (rowValues.length != cols) {
            throw new IllegalArgumentException("row length must equal current cols: expected "
                    + cols + " but was " + rowValues.length);
        }
        ensureColCapacity(cols);
        ensureRowCapacity(rows + 1);
        // Shift the reference block down by one. System.arraycopy performs a
        // memmove, so overlapping source/dest is handled correctly.
        if (rows - index > 0) {
            System.arraycopy(data, index, data, index + 1, rows - index);
        }
        // Replace the aliased slot with a brand new array, then copy the values.
        data[index] = new int[Math.max(colCapacity, INITIAL_COLS)];
        System.arraycopy(rowValues, 0, data[index], 0, cols);
        rows++;
    }

    /**
     * Remove the row at {@code index} and return its values.
     *
     * The remaining rows are shifted UP via {@link System#arraycopy}. The slot
     * that becomes free (the previous last logical slot) is set to {@code null}
     * so no dangling/duplicate reference survives and the GC can reclaim it.
     */
    public int[] removeRow(int index) {
        if (index < 0 || index >= rows) {
            throw new IndexOutOfBoundsException("row index out of bounds: " + index);
        }
        int[] removed = new int[cols];
        System.arraycopy(data[index], 0, removed, 0, cols);

        // Shift references up: data[index+1..] -> data[index..].
        if (rows - index - 1 > 0) {
            System.arraycopy(data, index + 1, data, index, rows - index - 1);
        }
        // Anti-fragmentation: the previously last slot now holds a stale
        // duplicate reference; null it out so the int[] can be collected.
        data[rows - 1] = null;

        rows--;
        if (rows == 0) cols = 0;
        return removed;
    }

    /**
     * Append a column at the end. If the matrix is empty this first insertion
     * defines {@code rows}.
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
                throw new IllegalArgumentException("col length must equal current rows: expected "
                        + rows + " but was " + colValues.length);
            }
            ensureColCapacity(cols + 1);
        }
        for (int r = 0; r < rows; r++) {
            data[r][cols] = colValues[r];
        }
        cols++;
    }

    /**
     * Insert a column at {@code index} (valid range {@code 0..cols} inclusive).
     * Each row is widened by shifting its tail right one cell with
     * {@link System#arraycopy}; the new cell defaults to {@code 0}.
     */
    public void addCol(int index, int[] colValues) {
        Objects.requireNonNull(colValues, "colValues must not be null");
        if (index < 0 || index > cols) {
            throw new IndexOutOfBoundsException("col index out of bounds: " + index);
        }
        if (rows == 0 && cols == 0) {
            rows = colValues.length;
            ensureRowCapacity(rows);
            cols = 0; // incremented below
            ensureColCapacity(Math.max(1, cols + 1));
        } else {
            if (colValues.length != rows) {
                throw new IllegalArgumentException("col length must equal current rows: expected "
                        + rows + " but was " + colValues.length);
            }
            ensureColCapacity(cols + 1);
        }
        for (int r = 0; r < rows; r++) {
            int[] row = data[r];
            // Shift the existing tail to the right to open slot 'index'.
            if (cols - index > 0) {
                System.arraycopy(row, index, row, index + 1, cols - index);
            }
            row[index] = colValues[r];
        }
        cols++;
    }

    /**
     * Remove the column at {@code index} and return its values.
     * Each row's tail is shifted LEFT with {@link System#arraycopy} and the
     * freed last column cell is zeroed (primitive ints have no references, so a
     * zero is sufficient for hygiene / re-growth correctness).
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
            row[cols - 1] = 0; // clear the now-unused trailing cell
        }
        cols--;
        if (cols == 0) rows = 0;
        return removed;
    }

    /** Zero out every cell but keep the logical dimensions unchanged. */
    public void clear() {
        for (int r = 0; r < rows; r++) {
            // Nullifying (rather than zeroing) releases references for GC.
            data[r] = null;
        }
        rows = 0;
        cols = 0;
    }

    /** Set every cell to {@code value}. */
    public void fill(int value) {
        for (int r = 0; r < rows; r++) {
            int[] row = data[r];
            for (int c = 0; c < cols; c++) {
                row[c] = value;
            }
        }
    }

    /** @return a flat row-major copy of all cells. */
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

    // ------------------------------------------------------------------
    // BINARY SERIALIZATION (null-row aware, DataInputStream/OutputStream)
    // ------------------------------------------------------------------

    /**
     * Serialise to a byte array using {@link DataOutputStream}.
     *
     * <p>Wire format:
     * <pre>
     *   int   rows
     *   int   cols
     *   for each row r in [0, rows):
     *       byte  flag   (1 = valid row, 0 = null row)
     *       if flag == 1: cols ints (row data, row-major)
     * </pre>
     * Null rows are preserved across a round-trip via the per-row flag byte.
     */
    public byte[] serializeToBytes() throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            writeTo(dos);
        }
        return baos.toByteArray();
    }

    /**
     * Write the matrix in the canonical binary format (see {@link #serializeToBytes}).
     * Does not close the supplied {@link DataOutputStream}.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(rows);
        out.writeInt(cols);
        for (int r = 0; r < rows; r++) {
            if (data[r] == null) {
                out.writeByte(0); // null row marker
            } else {
                out.writeByte(1); // valid row marker
                for (int c = 0; c < cols; c++) {
                    out.writeInt(data[r][c]);
                }
            }
        }
    }

    /**
     * Reconstruct a matrix from a byte array produced by {@link #serializeToBytes()}.
     *
     * @throws IOException if the stream is truncated or otherwise corrupted
     *         (negative dimensions, an invalid row flag, or an unexpected EOF).
     */
    public static DynamicIntMatrix deserializeFromBytes(byte[] bytes) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return readFrom(dis);
        }
    }

    /**
     * Read a matrix in the canonical binary format from an {@link InputStream}.
     * This low-level reader does NOT close the supplied stream.
     *
     * @throws IOException with a descriptive message on corruption.
     */
    public static DynamicIntMatrix readFrom(InputStream in) throws IOException {
        DataInputStream dis = (in instanceof DataInputStream)
                ? (DataInputStream) in
                : new DataInputStream(in);

        int r;
        int c;
        try {
            r = dis.readInt();
            c = dis.readInt();
        } catch (EOFException eof) {
            throw new IOException("Corrupted stream: unable to read matrix dimensions (rows, cols)", eof);
        }
        if (r < 0 || c < 0) {
            throw new IOException("Corrupted stream: negative dimensions (rows=" + r + ", cols=" + c + ")");
        }

        DynamicIntMatrix m = new DynamicIntMatrix(Math.max(r, 1), Math.max(c, 1));
        m.rows = r;
        m.cols = c;

        for (int i = 0; i < r; i++) {
            int flag;
            try {
                flag = dis.readByte();
            } catch (EOFException eof) {
                throw new IOException("Corrupted stream: unexpected EOF reading row flag at index " + i, eof);
            }
            if (flag == 1) {
                m.data[i] = new int[Math.max(c, 1)];
                for (int j = 0; j < c; j++) {
                    try {
                        m.data[i][j] = dis.readInt();
                    } catch (EOFException eof) {
                        throw new IOException("Corrupted stream: unexpected EOF reading cell ("
                                + i + "," + j + ")", eof);
                    }
                }
            } else if (flag == 0) {
                m.data[i] = null;
            } else {
                throw new IOException("Corrupted stream: invalid row flag " + flag
                        + " at row index " + i);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------
    // CSV (delegates to MatrixUtils, which owns the format/escaping)
    // ------------------------------------------------------------------

    /** Write this matrix to a CSV file (see {@link MatrixUtils#exportToCsv}). */
    public void exportToCsv(Path filePath) throws IOException {
        MatrixUtils.exportToCsv(this, filePath);
    }

    /** Read a matrix from a CSV file (see {@link MatrixUtils#importFromCsv}). */
    public static DynamicIntMatrix importFromCsv(Path filePath) throws IOException {
        return MatrixUtils.importFromCsv(filePath);
    }

    // ------------------------------------------------------------------
    // MATHEMATICAL OPERATIONS (all return a NEW matrix)
    // ------------------------------------------------------------------

    /**
     * Matrix addition: {@code C = A + B}.
     *
     * <p>MATHEMATICAL REQUIREMENT: addition is only defined for two matrices of
     * identical shape. Each result cell is {@code A[r][c] + B[r][c]}.
     *
     * @throws IllegalArgumentException if the dimensions do not match exactly.
     */
    public DynamicIntMatrix add(DynamicIntMatrix other) {
        Objects.requireNonNull(other, "other matrix must not be null");
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException("Matrix addition requires identical dimensions: "
                    + this.rows + "x" + this.cols + " vs " + other.rows + "x" + other.cols);
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
     * Matrix subtraction: {@code C = A - B}.
     *
     * <p>MATHEMATICAL REQUIREMENT: subtraction is only defined for two matrices
     * of identical shape. Each result cell is {@code A[r][c] - B[r][c]}.
     *
     * @throws IllegalArgumentException if the dimensions do not match exactly.
     */
    public DynamicIntMatrix subtract(DynamicIntMatrix other) {
        Objects.requireNonNull(other, "other matrix must not be null");
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException("Matrix subtraction requires identical dimensions: "
                    + this.rows + "x" + this.cols + " vs " + other.rows + "x" + other.cols);
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
     * Scalar multiplication: {@code C = A * s}.
     *
     * <p>Returns a NEW matrix; the original is never modified. Each cell becomes
     * {@code A[r][c] * s}.
     */
    public DynamicIntMatrix multiplyByScalar(int scalar) {
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
     * Matrix multiplication: {@code C = A * B}.
     *
     * <p>MATHEMATICAL REQUIREMENT: multiplication is defined only when the number
     * of columns in {@code A} equals the number of rows in {@code B}. The result
     * has dimensions {@code A.rows x B.cols}. For empty inner dimension the
     * result is the correctly sized zero matrix.
     *
     * @throws IllegalArgumentException if {@code this.cols != other.rows}.
     */
    public DynamicIntMatrix multiply(DynamicIntMatrix other) {
        Objects.requireNonNull(other, "other matrix must not be null");
        if (this.cols != other.rows) {
            throw new IllegalArgumentException("Matrix multiplication undefined: this.cols ("
                    + this.cols + ") must equal other.rows (" + other.rows + ")");
        }
        int r1 = this.rows;
        int c1 = this.cols; // == other.rows
        int c2 = other.cols;

        DynamicIntMatrix result = new DynamicIntMatrix(Math.max(r1, 1), Math.max(c2, 1));
        if (r1 == 0 || c1 == 0 || c2 == 0) {
            // Result is an r1 x c2 zero matrix, which is the mathematically
            // correct product when the inner dimension is empty.
            result.rows = r1;
            result.cols = c2;
            return result;
        }
        result.rows = r1;
        result.cols = c2;

        // Classic i,k,j triple loop over row-major storage (cache-friendly).
        for (int i = 0; i < r1; i++) {
            int[] rowA = this.data[i];
            int[] rowRes = result.data[i];
            for (int k = 0; k < c1; k++) {
                int valA = rowA[k];
                if (valA == 0) continue; // skip zero contributions
                int[] rowB = other.data[k];
                for (int j = 0; j < c2; j++) {
                    rowRes[j] += valA * rowB[j];
                }
            }
        }
        return result;
    }

    /**
     * Transpose: {@code C[r][c] = A[c][r]}. Always returns a NEW matrix; the
     * original is never modified. For an m x n matrix the result is n x m.
     */
    public DynamicIntMatrix transpose() {
        if (rows == 0 || cols == 0) {
            return new DynamicIntMatrix();
        }
        DynamicIntMatrix t = new DynamicIntMatrix(cols, rows);
        t.rows = cols;
        t.cols = rows;
        for (int r = 0; r < cols; r++) {
            for (int c = 0; c < rows; c++) {
                t.data[r][c] = this.data[c][r];
            }
        }
        return t;
    }

    /**
     * Rotate 90 degrees clockwise in place.
     *
     * <p>For an m x n matrix the result is an n x m matrix where element
     * {@code (r, c)} maps to {@code (c, n - 1 - r)}. This correctly handles
     * non-square matrices (rows become columns and vice-versa, reversed).
     * This mutates the receiver; nothing is returned.
     */
    public void rotate90Clockwise() {
        if (rows == 0 || cols == 0) return;
        int newRows = cols;
        int newCols = rows;
        int[][] newData = new int[newRows][];
        for (int i = 0; i < newRows; i++) {
            newData[i] = new int[newCols];
        }
        for (int r = 0; r < rows; r++) {
            int[] src = data[r];
            for (int c = 0; c < cols; c++) {
                newData[c][newCols - 1 - r] = src[c];
            }
        }
        this.data = newData;
        this.rows = newRows;
        this.cols = newCols;
        this.colCapacity = newCols;
    }

    /**
     * Extract a submatrix using HALF-OPEN ranges
     * {@code [startRow, endRow) x [startCol, endCol)}.
     *
     * <p>Returns a DEEP COPY (independent backing arrays via
     * {@link System#arraycopy}); mutating the result never affects this matrix.
     *
     * @throws IndexOutOfBoundsException if ranges are invalid or exceed bounds.
     */
    public DynamicIntMatrix getSubmatrix(int startRow, int startCol, int endRow, int endCol) {
        if (startRow < 0 || startCol < 0) {
            throw new IndexOutOfBoundsException("start indices must be >= 0: ("
                    + startRow + ", " + startCol + ")");
        }
        if (startRow > endRow || startCol > endCol) {
            throw new IndexOutOfBoundsException("start must not exceed end: ["
                    + startRow + "," + endRow + ") x [" + startCol + "," + endCol + ")");
        }
        if (endRow > rows || endCol > cols) {
            throw new IndexOutOfBoundsException("end indices exceed matrix bounds: ("
                    + endRow + "," + endCol + ") vs (" + rows + "," + cols + ")");
        }
        int newRows = endRow - startRow;
        int newCols = endCol - startCol;

        DynamicIntMatrix result = new DynamicIntMatrix(Math.max(newRows, 1), Math.max(newCols, 1));
        if (newRows == 0 || newCols == 0) {
            return result;
        }
        result.rows = newRows;
        result.cols = newCols;
        for (int r = 0; r < newRows; r++) {
            // Deep copy row by row into the fresh result rows.
            System.arraycopy(this.data[startRow + r], startCol, result.data[r], 0, newCols);
        }
        return result;
    }

    /**
     * Backwards-compatible alias for {@link #getSubmatrix(int, int, int, int)}
     * (original signature order: rowFrom, rowTo, colFrom, colTo).
     */
    public DynamicIntMatrix subMatrix(int rowFrom, int rowTo, int colFrom, int colTo) {
        return getSubmatrix(rowFrom, colFrom, rowTo, colTo);
    }

    /** @return a completely independent clone (deep copy) of this matrix. */
    public DynamicIntMatrix deepCopy() {
        int rc = Math.max(rows, 1);
        int cc = Math.max(cols, 1);
        DynamicIntMatrix copy = new DynamicIntMatrix(rc, cc);
        copy.rows = rows;
        copy.cols = cols;
        for (int r = 0; r < rows; r++) {
            if (data[r] == null) {
                copy.data[r] = null;
            } else {
                // copy.data[r] is already allocated by the constructor at the
                // correct column capacity; copy the live cells into it.
                System.arraycopy(data[r], 0, copy.data[r], 0, cols);
            }
        }
        return copy;
    }

    /**
     * Ensure the internal storage can hold at least {@code minRows} rows and
     * {@code minCols} columns without further reallocation. This is the
     * capacity hint equivalent of ArrayList.ensureCapacity.
     */
    public void ensureCapacity(int minRows, int minCols) {
        ensureRowCapacity(Math.max(minRows, 0));
        ensureColCapacity(Math.max(minCols, 0));
    }

    /**
     * Shrink the internal storage to exactly match the current logical
     * dimensions. The outer array is truncated to {@code rows} and every row
     * array is truncated to {@code cols}, reclaiming excess capacity.
     */
    public void trimToSize() {
        if (data.length != rows) {
            int[][] newData = new int[rows][];
            int toCopy = Math.min(data.length, rows);
            System.arraycopy(data, 0, newData, 0, toCopy);
            data = newData;
        }
        for (int r = 0; r < rows; r++) {
            if (data[r] != null && data[r].length != cols) {
                int[] nw = new int[cols];
                System.arraycopy(data[r], 0, nw, 0, cols);
                data[r] = nw;
            }
        }
        colCapacity = cols;
    }

    /**
     * Resize to {@code newRows x newCols}.
     *
     * <p>Newly exposed cells are zero-initialised. When shrinking, the trailing
     * cells/rows are cleaned (zeroed or nullified) so that re-growing later never
     * leaks stale data and never leaves dangling references.
     *
     * @throws IllegalArgumentException if either dimension is negative.
     */
    public void resize(int newRows, int newCols) {
        if (newRows < 0 || newCols < 0) {
            throw new IllegalArgumentException("dimensions must be non-negative");
        }
        if (newRows == 0 || newCols == 0) {
            clear();
            return;
        }
        int oldRows = rows;
        int oldCols = cols;

        ensureRowCapacity(newRows);
        ensureColCapacity(newCols);

        // Allocate any slots within the new row range that were nullified earlier.
        for (int r = 0; r < newRows; r++) {
            if (data[r] == null) {
                data[r] = new int[colCapacity];
            }
        }
        // Anti-fragmentation: nullify slots we are shrinking away.
        for (int r = newRows; r < oldRows; r++) {
            data[r] = null;
        }
        // Reconcile the column boundary across all retained rows.
        if (newCols > oldCols) {
            for (int r = 0; r < newRows; r++) {
                for (int c = oldCols; c < newCols; c++) {
                    data[r][c] = 0;
                }
            }
        } else if (newCols < oldCols) {
            for (int r = 0; r < newRows; r++) {
                for (int c = newCols; c < oldCols; c++) {
                    data[r][c] = 0;
                }
            }
        }
        this.rows = newRows;
        this.cols = newCols;
    }

    // ------------------------------------------------------------------
    // Backwards-compatible aliases
    // ------------------------------------------------------------------

    /** @deprecated Use {@link #add(DynamicIntMatrix)}. */
    @Deprecated
    public DynamicIntMatrix plus(DynamicIntMatrix other) {
        return add(other);
    }

    /** @deprecated Use {@link #subtract(DynamicIntMatrix)}. */
    @Deprecated
    public DynamicIntMatrix minus(DynamicIntMatrix other) {
        return subtract(other);
    }

    /** @deprecated Use {@link #multiplyByScalar(int)}. */
    @Deprecated
    public DynamicIntMatrix multiply(int scalar) {
        return multiplyByScalar(scalar);
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

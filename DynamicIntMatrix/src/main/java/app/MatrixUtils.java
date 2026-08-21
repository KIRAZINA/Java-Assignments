package app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Non-core helpers for {@link DynamicIntMatrix}: equality, copying, array
 * construction, and CSV import/export.
 *
 * <p>None of these helpers use {@code java.util.List}, streams or
 * {@code java.util.Arrays.copyOf}; CSV is parsed with manual character-level
 * scanning and matrix content is copied with {@link System#arraycopy}.
 */
public final class MatrixUtils {

    private MatrixUtils() { }

    /**
     * Compare two matrices for equality of dimensions and cell values.
     * (Does not support matrices that contain null rows.)
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
     * Return a completely independent (deep) copy of {@code src}.
     */
    public static DynamicIntMatrix copyOf(DynamicIntMatrix src) {
        Objects.requireNonNull(src, "src must not be null");
        return src.deepCopy();
    }

    /**
     * Build a matrix from a raw 2D int array. Every row must be non-null and the
     * same length; contents are copied with {@link System#arraycopy} so the
     * returned matrix does not retain references to the caller's arrays.
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
            if (arr[r] == null) {
                throw new NullPointerException("row " + r + " is null");
            }
            if (arr[r].length != clen) {
                throw new IllegalArgumentException("inconsistent row length at row " + r);
            }
            int[] row = new int[clen];
            System.arraycopy(arr[r], 0, row, 0, clen);
            m.addRow(row);
        }
        return m;
    }

    /**
     * Produce a CSV string (row-major) for debugging/small exports. Each row is
     * one line, values separated by commas and individually escaped.
     */
    public static String toCsv(DynamicIntMatrix m) {
        Objects.requireNonNull(m, "matrix must not be null");
        if (m.rows() == 0 || m.cols() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m.rows(); r++) {
            for (int c = 0; c < m.cols(); c++) {
                if (c > 0) sb.append(',');
                sb.append(escapeCsv(String.valueOf(m.get(r, c))));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // CSV EXPORT / IMPORT
    // ------------------------------------------------------------------

    /**
     * Write the matrix to a CSV file.
     *
     * <p>Format:
     * <pre>
     *   # rows=R, cols=C
     *   1,2,3
     *   4,5,6
     * </pre>
     * Cells are escaped with {@link #escapeCsv} so commas, quotes and newlines
     * inside a value would be handled correctly (integer cells never trigger
     * escaping, but the code path is identical for robustness).
     */
    public static void exportToCsv(DynamicIntMatrix m, Path filePath) throws IOException {
        Objects.requireNonNull(m, "matrix must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("# rows=");
            writer.write(Integer.toString(m.rows()));
            writer.write(", cols=");
            writer.write(Integer.toString(m.cols()));
            writer.newLine();
            for (int r = 0; r < m.rows(); r++) {
                for (int c = 0; c < m.cols(); c++) {
                    if (c > 0) writer.write(',');
                    writer.write(escapeCsv(String.valueOf(m.get(r, c))));
                }
                writer.newLine();
            }
        }
    }

    /**
     * Read a matrix from a CSV file written by {@link #exportToCsv}.
     *
     * <p>The dimension comment on the first line is used to validate the body;
     * malformed input (missing header, wrong field count, non-integer cells,
     * or a row/column mismatch) raises {@link IOException}.
     */
    public static DynamicIntMatrix importFromCsv(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Malformed CSV: missing dimension header line");
            }

            int rows;
            int cols;
            try {
                String body = header.trim();
                if (!body.startsWith("#")) {
                    throw new IOException("Malformed CSV: header must start with '#': " + header);
                }
                body = body.substring(1).trim();
                int rIdx = body.indexOf("rows=");
                int cIdx = body.indexOf("cols=");
                if (rIdx < 0 || cIdx < 0) {
                    throw new IOException("Malformed CSV header, expected 'rows=' and 'cols=': " + header);
                }
                int rEnd = body.indexOf(',', rIdx);
                String rs = (rEnd < 0 ? body.substring(rIdx + 5) : body.substring(rIdx + 5, rEnd)).trim();
                String cs = body.substring(cIdx + 5).trim();
                rows = Integer.parseInt(rs);
                cols = Integer.parseInt(cs);
            } catch (NumberFormatException nfe) {
                throw new IOException("Malformed CSV header (cannot parse dimensions): " + header, nfe);
            }

            if (rows < 0 || cols < 0) {
                throw new IOException("Malformed CSV: negative dimensions rows=" + rows + " cols=" + cols);
            }

            DynamicIntMatrix m = new DynamicIntMatrix();
            if (rows == 0 || cols == 0) {
                return m;
            }

            int r = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue; // tolerate blank lines
                if (r >= rows) {
                    throw new IOException("Malformed CSV: more data rows than declared (" + rows + ")");
                }
                String[] fields = parseCsvFields(line);
                if (fields.length != cols) {
                    throw new IOException("Malformed CSV: row " + r + " has " + fields.length
                            + " fields, expected " + cols);
                }
                int[] row = new int[cols];
                for (int c = 0; c < cols; c++) {
                    try {
                        row[c] = Integer.parseInt(fields[c].trim());
                    } catch (NumberFormatException nfe) {
                        throw new IOException("Malformed CSV: cell (" + r + "," + c
                                + ") is not a valid integer: '" + fields[c] + "'", nfe);
                    }
                }
                m.addRow(row);
                r++;
            }
            if (r != rows) {
                throw new IOException("Malformed CSV: expected " + rows + " data rows but read " + r);
            }
            return m;
        }
    }

    // ------------------------------------------------------------------
    // CSV ESCAPING / PARSING (manual, no java.util.List / streams)
    // ------------------------------------------------------------------

    /**
     * Escape a single CSV field value.
     *
     * <p>Per RFC 4180: a field containing a comma, double-quote, CR or LF must
     * be wrapped in double quotes; embedded double quotes are doubled
     * ({@code ""}).
     */
    public static String escapeCsv(String value) {
        Objects.requireNonNull(value, "value must not be null");
        boolean needsQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ',' || ch == '"' || ch == '\n' || ch == '\r') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"') {
                sb.append("\"\"");
            } else {
                sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Split a single CSV line into its fields, honouring double-quoted fields
     * (including embedded commas and doubled quotes). Embedded newlines within
     * a quoted field are not supported here; integer matrix cells never contain
     * them, and the exporter never produces them.
     */
    public static String[] parseCsvFields(String line) {
        Objects.requireNonNull(line, "line must not be null");
        // First pass: count the fields so we can allocate the exact array.
        int fieldCount = 1;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                fieldCount++;
            }
        }
        String[] fields = new String[fieldCount];

        int f = 0;
        int n = line.length();
        int i = 0;
        StringBuilder sb = new StringBuilder();
        inQuotes = false;
        while (i < n) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && line.charAt(i + 1) == '"') {
                        sb.append('"'); // escaped quote
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                sb.append(ch);
                i++;
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
                i++;
                continue;
            }
            if (ch == ',') {
                fields[f++] = sb.toString();
                sb.setLength(0);
                i++;
                continue;
            }
            sb.append(ch);
            i++;
        }
        fields[f] = sb.toString();
        return fields;
    }
}

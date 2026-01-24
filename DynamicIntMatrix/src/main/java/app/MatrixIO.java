package app;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utilities for binary serialization/deserialization of DynamicIntMatrix.
 *
 * Format:
 * int rows
 * int cols
 * rows * cols ints in row-major order
 */
public final class MatrixIO {
    private MatrixIO() { }

    public static void writeTo(DynamicIntMatrix matrix, OutputStream out) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(out)) {
            matrix.writeTo(dos);
            dos.flush();
        }
    }

    public static DynamicIntMatrix readFrom(InputStream in) throws IOException {
        // Delegate to DynamicIntMatrix.readFrom, but preserve "close stream" behavior
        // as originally implemented in this utility.
        try (InputStream is = in) {
            return DynamicIntMatrix.readFrom(is);
        }
    }
}

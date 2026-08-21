package app;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary (de)serialization utilities for {@link DynamicIntMatrix}.
 *
 * <p>The wire format is defined by {@link DynamicIntMatrix#writeTo(DataOutputStream)}
 * and {@link DynamicIntMatrix#readFrom(InputStream)}:
 * <pre>
 *   int   rows
 *   int   cols
 *   for each row r in [0, rows):
 *       byte  flag  (1 = valid row, 0 = null row)
 *       if flag == 1: cols ints (row data, row-major)
 * </pre>
 *
 * <p>This class never uses {@code ObjectOutputStream}; it relies purely on
 * {@code DataInputStream}/{@code DataOutputStream} for deterministic, compact,
 * primitive-only encoding.
 */
public final class MatrixIO {

    private MatrixIO() { }

    /**
     * Write the matrix to an {@link OutputStream} in the canonical binary format.
     */
    public static void writeTo(DynamicIntMatrix matrix, OutputStream out) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(out)) {
            matrix.writeTo(dos);
            dos.flush();
        }
    }

    /**
     * Serialize the matrix to a byte array (convenience wrapper).
     */
    public static byte[] serializeToBytes(DynamicIntMatrix matrix) throws IOException {
        return matrix.serializeToBytes();
    }

    /**
     * Read a matrix from an {@link InputStream} in the canonical binary format.
     *
     * <p>This utility owns the supplied stream for the duration of the read and
     * closes it (unlike {@link DynamicIntMatrix#readFrom(InputStream)}, which
     * leaves the caller responsible for the stream).
     *
     * @throws IOException with a descriptive message if the stream is corrupted.
     */
    public static DynamicIntMatrix readFrom(InputStream in) throws IOException {
        // Wrap so a plain InputStream still gets buffering; reuse an existing
        // DataInputStream as-is. The try-with-resources closes the originating
        // stream at the end of the read.
        DataInputStream dis = (in instanceof DataInputStream)
                ? (DataInputStream) in
                : new DataInputStream(new BufferedInputStream(in));
        try {
            return DynamicIntMatrix.readFrom(dis);
        } finally {
            dis.close();
        }
    }

    /**
     * Deserialize a matrix from a byte array (convenience wrapper).
     */
    public static DynamicIntMatrix deserializeFromBytes(byte[] bytes) throws IOException {
        return DynamicIntMatrix.deserializeFromBytes(bytes);
    }
}

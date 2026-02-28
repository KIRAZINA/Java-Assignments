package app.converter;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Immutable record capturing the result of a single file conversion.
 *
 * @param sourcePath      path of the input file
 * @param resultPath      path of the output file (null if conversion failed before writing)
 * @param inputSizeBytes  size of the source file in bytes
 * @param outputSizeBytes size of the output file in bytes (0 on failure)
 * @param durationMs      wall-clock duration of the conversion in milliseconds
 * @param status          {@link ConversionStatus#SUCCESS} or {@link ConversionStatus#FAILURE}
 * @param diagnostics     human-readable message (error description on failure, empty on success)
 * @param timestamp       when the conversion was attempted
 * @param rowCount        number of top-level rows/entries in the source document
 *                        (array size, or object key count, or total line count as fallback)
 */
public record ConversionResult(
        Path sourcePath,
        Path resultPath,
        long inputSizeBytes,
        long outputSizeBytes,
        long durationMs,
        ConversionStatus status,
        String diagnostics,
        Instant timestamp,
        int rowCount
) {
    /** Convenience factory for a successful conversion. */
    public static ConversionResult success(Path src, Path dest,
                                           long inBytes, long outBytes,
                                           long durationMs, int rowCount) {
        return new ConversionResult(src, dest, inBytes, outBytes,
                durationMs, ConversionStatus.SUCCESS, "", Instant.now(), rowCount);
    }

    /** Convenience factory for a failed conversion. */
    public static ConversionResult failure(Path src, long inBytes,
                                           long durationMs, String message, int rowCount) {
        return new ConversionResult(src, null, inBytes, 0L,
                durationMs, ConversionStatus.FAILURE, message, Instant.now(), rowCount);
    }

    /** Convenience factory for a failed conversion when row count is unknown. */
    public static ConversionResult failure(Path src, long inBytes,
                                           long durationMs, String message) {
        return failure(src, inBytes, durationMs, message, 0);
    }
}

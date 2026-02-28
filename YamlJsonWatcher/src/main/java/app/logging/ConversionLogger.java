package app.logging;

import app.converter.ConversionResult;
import app.converter.ConversionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes structured log lines for each conversion outcome.
 *
 * <p>Two formats are used depending on file size:
 * <ul>
 *   <li><strong>Normal files</strong> (rows &lt; threshold) — full JSON log entry with all fields.</li>
 *   <li><strong>Large files</strong> (rows &ge; threshold) — compact single-line summary at DEBUG
 *       level to avoid flooding the log. A brief INFO line confirms the operation completed.</li>
 * </ul>
 *
 * <p>The threshold defaults to 500 rows and is configurable via {@code config.yaml}
 * ({@code largeFileRowThreshold}) or the {@code LARGE_FILE_THRESHOLD} environment variable.
 */
public class ConversionLogger {

    private static final Logger log = LoggerFactory.getLogger(ConversionLogger.class);
    private static final int DEFAULT_LARGE_FILE_THRESHOLD = 500;

    private final int largeFileRowThreshold;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Creates a logger with the default threshold (500 rows). */
    public ConversionLogger() {
        this.largeFileRowThreshold = DEFAULT_LARGE_FILE_THRESHOLD;
    }

    /** Creates a logger with a custom threshold. */
    public ConversionLogger(int largeFileRowThreshold) {
        this.largeFileRowThreshold = largeFileRowThreshold;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Logs a successful conversion.
     * Normal files → full JSON entry at INFO.
     * Large files  → compact summary at INFO + full detail at DEBUG.
     */
    public void logSuccess(ConversionResult result) {
        if (isLargeFile(result)) {
            log.info(buildCompactEntry(result));
            log.debug(buildFullEntry(result));
        } else {
            log.info(buildFullEntry(result));
        }
    }

    /**
     * Logs a failed conversion.
     * Always uses the full entry at ERROR level (failures are always important).
     */
    public void logFailure(ConversionResult result) {
        log.error(buildFullEntry(result));
    }

    /**
     * Dispatches to {@link #logSuccess} or {@link #logFailure} based on status.
     *
     * @param result the conversion result to log
     */
    public void log(ConversionResult result) {
        if (result.status() == ConversionStatus.SUCCESS) {
            logSuccess(result);
        } else {
            logFailure(result);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private boolean isLargeFile(ConversionResult r) {
        return r.rowCount() >= largeFileRowThreshold;
    }

    /**
     * Full JSON log line — all fields.
     * Used for normal files and always for failures.
     */
    private String buildFullEntry(ConversionResult r) {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp",   r.timestamp().toString());
        node.put("source",      r.sourcePath() != null ? r.sourcePath().toString() : "");
        node.put("result",      r.resultPath() != null ? r.resultPath().toString() : "");
        node.put("inputBytes",  r.inputSizeBytes());
        node.put("outputBytes", r.outputSizeBytes());
        node.put("durationMs",  r.durationMs());
        node.put("rowCount",    r.rowCount());
        node.put("status",      r.status().name());
        node.put("diagnostics", r.diagnostics() != null ? r.diagnostics() : "");
        return serialize(node);
    }

    /**
     * Compact log line for large files — shows only the filename (not full path),
     * byte sizes, duration, row count, and status. No diagnostics field on success.
     */
    private String buildCompactEntry(ConversionResult r) {
        String srcName  = r.sourcePath() != null  ? r.sourcePath().getFileName().toString()  : "";
        String destName = r.resultPath() != null   ? r.resultPath().getFileName().toString()   : "";

        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp",   r.timestamp().toString());
        node.put("source",      srcName);
        node.put("result",      destName);
        node.put("inputBytes",  r.inputSizeBytes());
        node.put("outputBytes", r.outputSizeBytes());
        node.put("durationMs",  r.durationMs());
        node.put("rowCount",    r.rowCount());
        node.put("status",      r.status().name());
        node.put("largeFile",   true);
        return serialize(node);
    }

    private String serialize(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"FAILURE\",\"diagnostics\":\"Logger serialisation error: " + e.getMessage() + "\"}";
        }
    }
}

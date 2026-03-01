package app.config;

import java.util.List;

/**
 * Immutable configuration record for the YamlJsonWatcher service.
 * All values are loaded from config.yaml and may be overridden by environment variables.
 */
public record AppConfig(
        String inputDir,
        String processingDir,
        String outputDir,
        String archiveDir,
        String errorDir,
        List<String> extensions,
        long debounceMs,
        String logLevel,
        String logFilePath,
        int largeFileRowThreshold
) {
}

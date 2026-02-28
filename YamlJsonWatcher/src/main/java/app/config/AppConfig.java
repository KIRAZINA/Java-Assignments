package app.config;

import java.util.List;

/**
 * Immutable configuration record for the YamlJsonWatcher service.
 * All values are loaded from config.yaml and may be overridden by environment variables.
 */
public record AppConfig(
        String targetDir,
        String outputDir,
        List<String> extensions,
        long debounceMs,
        String logLevel,
        String logFilePath,
        int largeFileRowThreshold
) {
    /** Returns the effective output directory: outputDir if set, otherwise targetDir. */
    public String effectiveOutputDir() {
        return (outputDir == null || outputDir.isBlank()) ? targetDir : outputDir;
    }
}

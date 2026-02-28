package app.watcher;

import app.config.AppConfig;
import app.converter.ConversionResult;
import app.converter.FileConverter;
import app.logging.ConversionLogger;
import app.validator.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Handles a single file-change event detected by {@link DirectoryWatcher}.
 *
 * <p>Processing steps:
 * <ol>
 *   <li>Filter — ignore temp files, wrong extensions, subdirectories</li>
 *   <li>Validate — confirm the file is syntactically valid</li>
 *   <li>Convert — produce the output file</li>
 *   <li>Log — write a structured log entry</li>
 * </ol>
 *
 * <p>All exceptions are caught internally; an error log entry is produced without
 * rethrowing so that the watcher loop stays alive.
 */
public class FileEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FileEventHandler.class);

    private final AppConfig       config;
    private final FileValidator   validator;
    private final FileConverter   converter;
    private final ConversionLogger conversionLogger;

    public FileEventHandler(AppConfig config) {
        this.config           = config;
        this.validator        = new FileValidator();
        this.converter        = new FileConverter();
        this.conversionLogger = new ConversionLogger(config.largeFileRowThreshold());
    }

    /** Constructor for testing with injected dependencies. */
    FileEventHandler(AppConfig config,
                     FileValidator validator,
                     FileConverter converter,
                     ConversionLogger conversionLogger) {
        this.config           = config;
        this.validator        = validator;
        this.converter        = converter;
        this.conversionLogger = conversionLogger;
    }

    /**
     * Processes the given path as a potential conversion candidate.
     *
     * @param path the file that triggered the watch event
     */
    public void handle(Path path) {
        try {
            if (!shouldProcess(path)) {
                return;
            }

            String fileName  = path.getFileName().toString().toLowerCase();
            boolean isJson   = fileName.endsWith(".json");
            boolean isYaml   = fileName.endsWith(".yaml") || fileName.endsWith(".yml");

            if (isJson) {
                handleJson(path);
            } else if (isYaml) {
                handleYaml(path);
            }
        } catch (Exception e) {
            log.error("Unexpected error while handling file {}: {}", path, e.getMessage(), e);
        }
    }

    // ── Internal logic ─────────────────────────────────────────────────────────

    private void handleJson(Path src) {
        Optional<String> validationError = validator.validateJson(src);
        if (validationError.isPresent()) {
            ConversionResult failure = ConversionResult.failure(src, sizeOf(src), 0L,
                    "Validation failed: " + validationError.get());
            conversionLogger.logFailure(failure);
            return;
        }

        Path dest   = buildDestPath(src, ".yaml");
        ConversionResult result = converter.convertJsonToYaml(src, dest);
        conversionLogger.log(result);
    }

    private void handleYaml(Path src) {
        Optional<String> validationError = validator.validateYaml(src);
        if (validationError.isPresent()) {
            ConversionResult failure = ConversionResult.failure(src, sizeOf(src), 0L,
                    "Validation failed: " + validationError.get());
            conversionLogger.logFailure(failure);
            return;
        }

        Path dest   = buildDestPath(src, ".json");
        ConversionResult result = converter.convertYamlToJson(src, dest);
        conversionLogger.log(result);
    }

    /**
     * Returns true only if the file should be processed.
     * Skips directories, temp files, and files with non-target extensions.
     */
    private boolean shouldProcess(Path path) {
        if (Files.isDirectory(path)) {
            return false;
        }

        String name = path.getFileName().toString();

        // Skip temporary / hidden files
        if (name.startsWith("~") || name.startsWith(".") || name.endsWith(".tmp")) {
            log.debug("Skipping temp/hidden file: {}", name);
            return false;
        }

        String lower = name.toLowerCase();
        List<String> extensions = config.extensions();
        boolean matched = extensions.stream().anyMatch(lower::endsWith);
        if (!matched) {
            log.debug("Skipping file with unsupported extension: {}", name);
        }
        return matched;
    }

    /**
     * Builds the destination path, replacing the source extension with {@code newExt}
     * and routing the output to the configured output directory.
     */
    private Path buildDestPath(Path src, String newExt) {
        String srcName   = src.getFileName().toString();
        String baseName  = srcName.contains(".")
                ? srcName.substring(0, srcName.lastIndexOf('.'))
                : srcName;
        String destName  = baseName + newExt;

        Path outputDir   = Paths.get(config.effectiveOutputDir());
        return outputDir.resolve(destName);
    }

    private long sizeOf(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }
}

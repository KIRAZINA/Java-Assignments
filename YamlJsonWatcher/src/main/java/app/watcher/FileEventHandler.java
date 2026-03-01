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

            Path processingDir = Paths.get(config.processingDir());
            Path processingPath = processingDir.resolve(path.getFileName());

            try {
                Files.move(path, processingPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.FileSystemException e) {
                // File might be locked by OS (still copying)
                log.debug("File is locked, skipping for now: {}", path.getFileName());
                return;
            } catch (IOException e) {
                log.error("Failed to move file to processing directory: {}", e.getMessage());
                return;
            }

            String fileName  = processingPath.getFileName().toString().toLowerCase();
            boolean isJson   = fileName.endsWith(".json");
            boolean isYaml   = fileName.endsWith(".yaml") || fileName.endsWith(".yml");

            if (isJson) {
                handleJson(processingPath);
            } else if (isYaml) {
                handleYaml(processingPath);
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
            moveToError(src, "Validation failed: " + validationError.get());
            return;
        }

        Path dest   = buildDestPath(src, ".yaml");
        ConversionResult result = converter.convertJsonToYaml(src, dest);
        if (result.status() == app.converter.ConversionStatus.SUCCESS) {
            conversionLogger.log(result);
            moveToArchive(src);
        } else {
            conversionLogger.logFailure(result);
            moveToError(src, result.diagnostics());
        }
    }

    private void handleYaml(Path src) {
        Optional<String> validationError = validator.validateYaml(src);
        if (validationError.isPresent()) {
            ConversionResult failure = ConversionResult.failure(src, sizeOf(src), 0L,
                    "Validation failed: " + validationError.get());
            conversionLogger.logFailure(failure);
            moveToError(src, "Validation failed: " + validationError.get());
            return;
        }

        Path dest   = buildDestPath(src, ".json");
        ConversionResult result = converter.convertYamlToJson(src, dest);
        if (result.status() == app.converter.ConversionStatus.SUCCESS) {
            conversionLogger.log(result);
            moveToArchive(src);
        } else {
            conversionLogger.logFailure(result);
            moveToError(src, result.diagnostics());
        }
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

        Path outputDir   = Paths.get(config.outputDir());
        return outputDir.resolve(destName);
    }

    private void moveToArchive(Path src) {
        String archiveDirStr = config.archiveDir();
        if (archiveDirStr == null || archiveDirStr.isBlank()) {
            try { Files.deleteIfExists(src); } catch (IOException ignored) {}
            return;
        }
        try {
            Path archiveDir = Paths.get(archiveDirStr);
            Files.createDirectories(archiveDir);
            Files.move(src, archiveDir.resolve(src.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to move {} to archive: {}", src.getFileName(), e.getMessage());
        }
    }

    private void moveToError(Path src, String errorMsg) {
        String errorDirStr = config.errorDir();
        if (errorDirStr == null || errorDirStr.isBlank()) {
            return; // left in processing
        }
        try {
            Path errorDir = Paths.get(errorDirStr);
            Files.createDirectories(errorDir);
            Path dest = errorDir.resolve(src.getFileName());
            Files.move(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(errorDir.resolve(src.getFileName().toString() + ".error.log"), errorMsg);
        } catch (IOException e) {
            log.error("Failed to move {} to error directory: {}", src.getFileName(), e.getMessage());
        }
    }

    private long sizeOf(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }
}

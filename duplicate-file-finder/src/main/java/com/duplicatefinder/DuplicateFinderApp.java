package com.duplicatefinder;

import com.duplicatefinder.cli.CommandLineParser;
import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.service.DirectoryScanner;
import com.duplicatefinder.service.DuplicateDetector;
import com.duplicatefinder.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Main application class for finding and managing duplicate files.
 */
public class DuplicateFinderApp {
    private static final Logger logger = LoggerFactory.getLogger(DuplicateFinderApp.class);

    public static void main(String[] args) {
        CommandLineParser parser = new CommandLineParser();
        parser.parse(args);

        List<String> directories = parser.getDirectories();
        if (directories.isEmpty()) {
            logger.error("No directories specified. Use --help for usage information.");
            System.err.println("Error: No directories specified. Use --help for usage information.");
            System.exit(1);
        }

        try {
            Map<String, List<String>> allDuplicates = new HashMap<>();
            
            // Scan all directories
            for (String dir : directories) {
                logger.info("Processing directory: {}", dir);
                
                Path dirPath = Path.of(dir);
                if (!Files.isDirectory(dirPath)) {
                    logger.warn("Path is not a directory, skipping: {}", dir);
                    continue;
                }

                // Create scanner with appropriate settings
                DirectoryScanner scanner = new DirectoryScanner(
                    dirPath,
                    parser.getMaxDepth(),
                    parser.getMinSize(),
                    false
                );

                // Set progress listener
                scanner.setProgressListener(count -> 
                    logger.info("Scanned {} files...", count)
                );

                // Scan directory
                List<FileInfo> files = scanner.scan();
                logger.info("Found {} files in {}", files.size(), dir);

                // Detect duplicates
                DuplicateDetector detector = new DuplicateDetector();
                detector.setCaseInsensitive(parser.isCaseInsensitive());
                detector.setProgressListener(count -> 
                    logger.info("Processed {} candidates...", count)
                );

                Map<String, List<String>> duplicates = detector.detectDuplicates(files);
                allDuplicates.putAll(duplicates);
            }

            // Display results
            if (allDuplicates.isEmpty()) {
                System.out.println("No duplicates found.");
                logger.info("No duplicates found.");
            } else {
                displayDuplicates(allDuplicates);

                // Handle deletion if requested
                if (parser.isDelete()) {
                    handleDeletion(allDuplicates, parser);
                }

                // Export results if requested
                if (parser.getExport() != null) {
                    exportResults(allDuplicates, parser.getExport());
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Displays duplicate files information.
     */
    private static void displayDuplicates(Map<String, List<String>> duplicates) {
        System.out.println("\n=== Duplicate Files Found ===\n");
        
        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            System.out.println("Original: " + entry.getKey());
            for (String duplicate : entry.getValue()) {
                if (!duplicate.equals(entry.getKey())) {
                    System.out.println("  Duplicate: " + duplicate);
                }
            }
            System.out.println();
        }
    }

    /**
     * Handles deletion of duplicate files.
     */
    private static void handleDeletion(Map<String, List<String>> duplicates, CommandLineParser parser) {
        logger.info("Starting deletion process...");
        int deletedCount = 0;

        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            String keepFile = entry.getKey();
            List<String> filesToDelete = new ArrayList<>(entry.getValue());
            filesToDelete.remove(keepFile);

            for (String filePath : filesToDelete) {
                if (FileUtils.deleteFile(Path.of(filePath))) {
                    deletedCount++;
                }
            }
        }

        logger.info("Deleted {} duplicate files", deletedCount);
        System.out.println("\nDeleted " + deletedCount + " duplicate files.");
    }

    /**
     * Exports results to JSON or CSV format.
     */
    private static void exportResults(Map<String, List<String>> duplicates, String format) {
        logger.info("Exporting results to {} format...", format);

        if ("json".equalsIgnoreCase(format)) {
            exportAsJson(duplicates);
        } else if ("csv".equalsIgnoreCase(format)) {
            exportAsCsv(duplicates);
        } else {
            logger.warn("Unknown export format: {}", format);
        }
    }

    /**
     * Exports results in JSON format.
     */
    private static void exportAsJson(Map<String, List<String>> duplicates) {
        // TODO: Implement JSON export (use java.json or simple StringBuilder)
        System.out.println("JSON export not yet implemented");
        logger.info("JSON export not yet implemented");
    }

    /**
     * Exports results in CSV format.
     */
    private static void exportAsCsv(Map<String, List<String>> duplicates) {
        // TODO: Implement CSV export
        System.out.println("CSV export not yet implemented");
        logger.info("CSV export not yet implemented");
    }
}

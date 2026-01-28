package com.duplicatefinder;

import com.duplicatefinder.cli.CommandLineParser;
import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.service.DirectoryScanner;
import com.duplicatefinder.service.DuplicateDetector;
import com.duplicatefinder.util.FileUtils;
import com.duplicatefinder.util.HashUtil;
import com.duplicatefinder.util.ProgressBar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
            int totalFiles = 0;
            
            // First pass: count total files for progress bar
            for (String dir : directories) {
                Path dirPath = Path.of(dir);
                if (Files.isDirectory(dirPath)) {
                    DirectoryScanner counter = new DirectoryScanner(dirPath, parser.getMaxDepth(), parser.getMinSize(), false);
                    try {
                        List<FileInfo> files = counter.scan();
                        totalFiles += files.size();
                    } catch (IOException e) {
                        logger.warn("Could not scan directory for counting: {}", dir, e);
                    }
                }
            }
            
            ProgressBar scanProgressBar = new ProgressBar(totalFiles, "Scanning files");
            
            // Scan all directories with progress tracking
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

                // Set progress listener that updates both logger and progress bar
                AtomicInteger scannedCount = new AtomicInteger(0);
                scanner.setProgressListener(count -> {
                    int increment = count - scannedCount.get();
                    scanProgressBar.update(increment);
                    scannedCount.set(count);
                    
                    if (count % 100 == 0) {
                        logger.info("Scanned {} files...", count);
                    }
                });

                // Scan directory
                List<FileInfo> files = scanner.scan();
                logger.info("Found {} files in {}", files.size(), dir);

                // Detect duplicates
                DuplicateDetector detector = new DuplicateDetector();
                detector.setCaseInsensitive(parser.isCaseInsensitive());
                
                // Create progress bar for CRC32 computation
                ProgressBar crcProgressBar = new ProgressBar(files.size(), "Computing checksums");
                detector.setProgressListener(count -> {
                    crcProgressBar.setCurrent(count);
                    if (count % 10 == 0) {
                        logger.info("Processed {} candidates...", count);
                    }
                });

                Map<String, List<String>> duplicates = detector.detectDuplicates(files);
                crcProgressBar.complete();
                
                allDuplicates.putAll(duplicates);
            }
            
            scanProgressBar.complete();

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
        } finally {
            // Save cache before exiting
            try {
                HashUtil.saveCache();
                logger.info("Cache saved successfully");
            } catch (Exception e) {
                logger.warn("Failed to save cache: {}", e.getMessage());
            }
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
            String keepFile = determineFileToKeep(entry.getValue(), parser);
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
     * Determines which file to keep based on the command line options.
     */
    private static String determineFileToKeep(List<String> files, CommandLineParser parser) {
        if (parser.isKeepNewest()) {
            return files.stream()
                .max(Comparator.comparing(file -> {
                    try {
                        return Files.getLastModifiedTime(Path.of(file)).toInstant();
                    } catch (IOException e) {
                        logger.warn("Could not get modification time for file: {}", file, e);
                        return Instant.MIN;
                    }
                }))
                .orElse(files.get(0));
        } else if (parser.isKeepOldest()) {
            return files.stream()
                .min(Comparator.comparing(file -> {
                    try {
                        return Files.getLastModifiedTime(Path.of(file)).toInstant();
                    } catch (IOException e) {
                        logger.warn("Could not get modification time for file: {}", file, e);
                        return Instant.MAX;
                    }
                }))
                .orElse(files.get(0));
        } else {
            // Default: keep the first file
            return files.get(0);
        }
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
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "duplicate_files_" + timestamp + ".json";
            
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("timestamp", LocalDateTime.now().toString());
            exportData.put("totalDuplicateGroups", duplicates.size());
            exportData.put("duplicates", duplicates);
            
            mapper.writeValue(Path.of(filename).toFile(), exportData);
            
            System.out.println("Results exported to: " + filename);
            logger.info("Results exported to JSON file: {}", filename);
        } catch (IOException e) {
            logger.error("Failed to export results to JSON", e);
            System.err.println("Error: Failed to export results to JSON - " + e.getMessage());
        }
    }

    /**
     * Exports results in CSV format.
     */
    private static void exportAsCsv(Map<String, List<String>> duplicates) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "duplicate_files_" + timestamp + ".csv";
            
            try (FileWriter writer = new FileWriter(filename)) {
                // Write header
                writer.write("Original File,Duplicate File,Group Size\n");
                
                // Write duplicate groups
                for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
                    String original = entry.getKey();
                    List<String> duplicateList = entry.getValue();
                    
                    for (String duplicate : duplicateList) {
                        if (!duplicate.equals(original)) {
                            writer.write(String.format("\"%s\",\"%s\",%d\n", 
                                original.replace("\"", "\"\""), 
                                duplicate.replace("\"", "\"\""), 
                                duplicateList.size()));
                        }
                    }
                }
            }
            
            System.out.println("Results exported to: " + filename);
            logger.info("Results exported to CSV file: {}", filename);
        } catch (IOException e) {
            logger.error("Failed to export results to CSV", e);
            System.err.println("Error: Failed to export results to CSV - " + e.getMessage());
        }
    }
}

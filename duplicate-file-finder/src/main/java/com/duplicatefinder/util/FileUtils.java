package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * Utility class for file operations.
 */
public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Checks if a path is a symbolic link.
     *
     * @param path Path to check
     * @return true if path is a symbolic link, false otherwise
     */
    public static boolean isSymbolicLink(Path path) {
        return Files.isSymbolicLink(path);
    }

    /**
     * Checks if a file is readable.
     *
     * @param path Path to check
     * @return true if file is readable, false otherwise
     */
    public static boolean isReadable(Path path) {
        return Files.isReadable(path);
    }

    /**
     * Safely deletes a file.
     *
     * @param path Path to the file to delete
     * @return true if deletion was successful, false otherwise
     */
    public static boolean deleteFile(Path path) {
        try {
            Files.delete(path);
            logger.info("Successfully deleted file: {}", path);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", path, e);
            return false;
        }
    }

    /**
     * Gets the size of a file.
     *
     * @param path Path to the file
     * @return File size in bytes
     * @throws IOException if file size cannot be determined
     */
    public static long getFileSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Counts files in a directory recursively.
     *
     * @param directory Directory path
     * @param maxDepth Maximum depth for traversal
     * @return Number of files found
     * @throws IOException if directory cannot be read
     */
    public static long countFiles(Path directory, int maxDepth) throws IOException {
        try (Stream<Path> paths = Files.walk(directory, maxDepth)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    /**
     * Formats file size to human-readable format.
     *
     * @param sizeInBytes Size in bytes
     * @return Formatted size string (e.g., "1.5 MB")
     */
    public static String formatFileSize(long sizeInBytes) {
        if (sizeInBytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(sizeInBytes) / Math.log10(1024));
        double size = sizeInBytes / Math.pow(1024, unitIndex);
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * Parses size string to bytes.
     *
     * @param sizeStr Size string (e.g., "1MB", "500KB")
     * @return Size in bytes
     */
    public static long parseSizeString(String sizeStr) {
        String upperCase = sizeStr.toUpperCase().trim();
        
        if (upperCase.endsWith("B")) {
            String numStr = upperCase.substring(0, upperCase.length() - 1).trim();
            long size = Long.parseLong(numStr);
            
            if (upperCase.endsWith("KB")) return size * 1024;
            if (upperCase.endsWith("MB")) return size * 1024 * 1024;
            if (upperCase.endsWith("GB")) return size * 1024 * 1024 * 1024;
            if (upperCase.endsWith("TB")) return size * 1024L * 1024 * 1024 * 1024;
        }
        
        return Long.parseLong(upperCase);
    }
}

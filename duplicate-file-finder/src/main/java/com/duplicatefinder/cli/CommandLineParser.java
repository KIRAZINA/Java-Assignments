package com.duplicatefinder.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple command-line argument parser.
 * Parses arguments in format: java App [options] dir1 [dir2 ...]
 */
public class CommandLineParser {
    private final List<String> directories = new ArrayList<>();
    private final Map<String, String> options = new HashMap<>();
    private boolean dryRun = true;
    private boolean delete = false;
    private boolean keepNewest = false;
    private boolean keepOldest = false;
    private String export = null;
    private long minSize = 0;
    private int maxDepth = 10;
    private boolean caseInsensitive = false;

    /**
     * Parses command-line arguments.
     *
     * @param args Command-line arguments
     */
    public void parse(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--")) {
                parseOption(arg);
            } else {
                directories.add(arg);
            }
        }
    }

    /**
     * Parses a single option.
     */
    private void parseOption(String arg) {
        String option = arg.substring(2); // Remove "--"

        if (option.equals("help")) {
            printHelp();
            System.exit(0);
        } else if (option.equals("dry-run")) {
            this.dryRun = true;
        } else if (option.equals("delete")) {
            this.delete = true;
            this.dryRun = false;
        } else if (option.equals("keep-newest")) {
            this.keepNewest = true;
        } else if (option.equals("keep-oldest")) {
            this.keepOldest = true;
        } else if (option.equals("case-insensitive")) {
            this.caseInsensitive = true;
        } else if (option.startsWith("export=")) {
            this.export = option.substring(7);
        } else if (option.startsWith("min-size=")) {
            String sizeStr = option.substring(9);
            this.minSize = parseSizeString(sizeStr);
        } else if (option.startsWith("max-depth=")) {
            this.maxDepth = Integer.parseInt(option.substring(10));
        } else {
            System.err.println("Unknown option: --" + option);
        }
    }

    /**
     * Parses size string (e.g., "1MB", "500KB") to bytes.
     */
    private long parseSizeString(String sizeStr) {
        String upperCase = sizeStr.toUpperCase().trim();
        
        if (upperCase.endsWith("KB")) {
            return Long.parseLong(upperCase.substring(0, upperCase.length() - 2).trim()) * 1024;
        } else if (upperCase.endsWith("MB")) {
            return Long.parseLong(upperCase.substring(0, upperCase.length() - 2).trim()) * 1024 * 1024;
        } else if (upperCase.endsWith("GB")) {
            return Long.parseLong(upperCase.substring(0, upperCase.length() - 2).trim()) * 1024 * 1024 * 1024;
        } else if (upperCase.endsWith("B")) {
            return Long.parseLong(upperCase.substring(0, upperCase.length() - 1).trim());
        }
        
        return Long.parseLong(upperCase);
    }

    /**
     * Prints help message.
     */
    private void printHelp() {
        System.out.println("""
            Duplicate File Finder
            
            Usage: java DuplicateFinderApp [options] <directory1> [directory2 ...]
            
            Options:
              --help                    Show this help message
              --dry-run                 Do not delete, only show (default)
              --delete                  Delete duplicates, keeping the first
              --keep-newest             Keep the newest file by modification date
              --keep-oldest             Keep the oldest file
              --export=json             Save results in JSON format
              --export=csv              Save results in CSV format
              --min-size=1MB            Ignore files smaller than specified size
              --max-depth=10            Maximum depth for directory scanning
              --case-insensitive        Ignore case in file names
            """);
    }

    // Getters
    public List<String> getDirectories() {
        return directories;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isDelete() {
        return delete;
    }

    public boolean isKeepNewest() {
        return keepNewest;
    }

    public boolean isKeepOldest() {
        return keepOldest;
    }

    public String getExport() {
        return export;
    }

    public long getMinSize() {
        return minSize;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }
}

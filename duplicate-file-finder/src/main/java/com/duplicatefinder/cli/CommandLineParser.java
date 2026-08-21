package com.duplicatefinder.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple data-holding POJO for command-line configuration.
 *
 * <p>This class intentionally has <em>no</em> picocli annotations.  Its role is to carry
 * the parsed settings as a plain Java object, keeping business logic decoupled from the
 * CLI framework.  Parsed values are populated either by the hand-rolled
 * {@link #parse(String[])} method (legacy) or by {@link DuplicateFinderCommand} (picocli path).
 * Both paths produce an identical, fully-populated {@code CommandLineParser} instance.</p>
 *
 * <p>Existing unit tests that construct {@code CommandLineParser} directly continue to work
 * unchanged, because this class has no dependency on picocli internals.</p>
 */
public class CommandLineParser {

    private List<String> directories   = new ArrayList<>();
    private boolean      dryRun        = true;
    private boolean      delete        = false;
    private boolean      keepNewest    = false;
    private boolean      keepOldest    = false;
    private String       export        = null;
    private long         minSize       = 0;
    private int          maxDepth      = 10;
    private boolean      caseInsensitive = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy hand-rolled parser (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses command-line arguments in the legacy {@code --flag} / {@code dir} format.
     * Kept for backward compatibility; the primary CLI path now goes through
     * {@link DuplicateFinderCommand} (picocli).
     *
     * @param args raw command-line arguments
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

    private void parseOption(String arg) {
        String option = arg.substring(2);

        switch (option) {
            case "help"             -> { printHelp(); System.exit(0); }
            case "dry-run"          -> this.dryRun = true;
            case "delete"           -> { this.delete = true; this.dryRun = false; }
            case "keep-newest"      -> this.keepNewest = true;
            case "keep-oldest"      -> this.keepOldest = true;
            case "case-insensitive" -> this.caseInsensitive = true;
            default -> {
                if (option.startsWith("export="))    this.export  = option.substring(7);
                else if (option.startsWith("min-size=")) this.minSize = parseSizeString(option.substring(9));
                else if (option.startsWith("max-depth=")) this.maxDepth = Integer.parseInt(option.substring(10));
                else System.err.println("Unknown option: --" + option);
            }
        }
    }

    private long parseSizeString(String s) {
        String upper = s.toUpperCase().trim();
        if (upper.endsWith("GB")) return Long.parseLong(upper.replace("GB","").trim()) * 1024L * 1024 * 1024;
        if (upper.endsWith("MB")) return Long.parseLong(upper.replace("MB","").trim()) * 1024L * 1024;
        if (upper.endsWith("KB")) return Long.parseLong(upper.replace("KB","").trim()) * 1024L;
        if (upper.endsWith("B"))  return Long.parseLong(upper.replace("B","").trim());
        return Long.parseLong(upper);
    }

    private void printHelp() {
        System.out.println("""
            Duplicate File Finder

            Usage: java DuplicateFinderApp [options] <directory1> [directory2 ...]

            Options:
              --help                  Show this help message
              --dry-run               Preview only (default)
              --delete                Delete duplicates, keeping one copy
              --keep-newest           Keep the newest file by modification date
              --keep-oldest           Keep the oldest file
              --export=json|csv       Export results
              --min-size=<size>       Ignore files smaller than size (e.g. 1MB)
              --max-depth=<n>         Maximum directory scan depth (default 10)
              --case-insensitive      Case-insensitive name comparison
            """);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters
    // ─────────────────────────────────────────────────────────────────────────

    public List<String> getDirectories()   { return directories;   }
    public boolean      isDryRun()         { return dryRun;        }
    public boolean      isDelete()         { return delete;        }
    public boolean      isKeepNewest()     { return keepNewest;    }
    public boolean      isKeepOldest()     { return keepOldest;    }
    public String       getExport()        { return export;        }
    public long         getMinSize()       { return minSize;       }
    public int          getMaxDepth()      { return maxDepth;      }
    public boolean      isCaseInsensitive(){ return caseInsensitive; }

    // ─────────────────────────────────────────────────────────────────────────
    // Setters — required by DuplicateFinderCommand (picocli path)
    // ─────────────────────────────────────────────────────────────────────────

    public void setDirectories(List<String> directories)     { this.directories    = directories;    }
    public void setDryRun(boolean dryRun)                    { this.dryRun         = dryRun;         }
    public void setDelete(boolean delete)                    { this.delete         = delete;         }
    public void setKeepNewest(boolean keepNewest)            { this.keepNewest     = keepNewest;     }
    public void setKeepOldest(boolean keepOldest)            { this.keepOldest     = keepOldest;     }
    public void setCaseInsensitive(boolean caseInsensitive)  { this.caseInsensitive = caseInsensitive; }
    public void setExport(String export)                     { this.export         = export;         }
    public void setMinSize(long minSize)                     { this.minSize        = minSize;        }
    public void setMaxDepth(int maxDepth)                    { this.maxDepth       = maxDepth;       }
}

package com.duplicatefinder.cli;

import com.duplicatefinder.cli.CommandLineParser;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Thin picocli {@link Command} wrapper that provides:
 * <ul>
 *   <li>Automatic {@code --help} / {@code --version} generation</li>
 *   <li>ANSI-aware usage text (picocli strips escape codes automatically on non-ANSI terminals)</li>
 *   <li>Proper exit-code propagation</li>
 * </ul>
 *
 * <p>Design intent: this class is purely a <em>façade</em>.  All business logic lives in
 * {@link com.duplicatefinder.DuplicateFinderApp}.  The existing {@link CommandLineParser}
 * POJO is populated here and passed through, preserving the existing test contracts for
 * {@code CommandLineParser} — no test changes are required to accommodate picocli.</p>
 *
 * <p>To invoke this class from {@code main()}:</p>
 * <pre>{@code
 *   int exitCode = new CommandLine(new DuplicateFinderCommand()).execute(args);
 *   System.exit(exitCode);
 * }</pre>
 */
@Command(
    name        = "duplicate-finder",
    mixinStandardHelpOptions = true,   // adds --help and --version automatically
    version     = "Duplicate File Finder 1.0",
    description = "Finds and optionally deletes duplicate files using a 3-level funnel strategy.",
    headerHeading  = "@|bold,cyan Duplicate File Finder|@%n%n",
    synopsisHeading = "@|bold Usage:|@ ",
    descriptionHeading = "%n@|bold Description:|@%n",
    optionListHeading  = "%n@|bold Options:|@%n",
    parameterListHeading = "%n@|bold Directories:|@%n",
    footer = "%n@|italic Use --dry-run (default) to preview, --delete to remove duplicates.|@"
)
public class DuplicateFinderCommand implements Callable<Integer> {

    @Parameters(
        paramLabel  = "<directory>",
        description = "One or more root directories to scan for duplicates.",
        arity       = "1..*"
    )
    private List<String> directories = new ArrayList<>();

    @Option(names = "--dry-run",
            description = "Preview duplicates without deleting anything (default behaviour).")
    private boolean dryRun = false;

    @Option(names = "--delete",
            description = "Delete duplicate files, keeping one copy per group.")
    private boolean delete = false;

    @Option(names = "--keep-newest",
            description = "When deleting, keep the most recently modified file.")
    private boolean keepNewest = false;

    @Option(names = "--keep-oldest",
            description = "When deleting, keep the oldest file by modification date.")
    private boolean keepOldest = false;

    @Option(names = "--case-insensitive",
            description = "Treat file names as case-insensitive during name-level grouping.")
    private boolean caseInsensitive = false;

    @Option(names = "--export",
            paramLabel = "<format>",
            description = "Export results. Valid values: ${COMPLETION-CANDIDATES}.",
            completionCandidates = ExportFormatCandidates.class)
    private String export = null;

    @Option(names = "--min-size",
            paramLabel = "<size>",
            description = "Ignore files smaller than this size (e.g. 1MB, 500KB, 1048576).")
    private String minSize = null;

    @Option(names = "--max-depth",
            paramLabel = "<depth>",
            description = "Maximum directory recursion depth (default: 10).")
    private int maxDepth = 10;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point invoked by picocli after argument parsing.
     * Populates a {@link CommandLineParser} POJO and delegates to
     * {@link com.duplicatefinder.DuplicateFinderApp#run(CommandLineParser)}.
     *
     * @return exit code (0 = success, non-zero = error)
     */
    @Override
    public Integer call() {
        CommandLineParser parser = new CommandLineParser();

        // Populate the POJO — this preserves the existing CommandLineParser contract
        // so that any tests that construct it directly still work unchanged.
        parser.setDirectories(directories);
        parser.setDryRun(dryRun || !delete);  // default to dry-run if --delete not given
        parser.setDelete(delete);
        parser.setKeepNewest(keepNewest);
        parser.setKeepOldest(keepOldest);
        parser.setCaseInsensitive(caseInsensitive);
        parser.setExport(export);
        parser.setMaxDepth(maxDepth);

        if (minSize != null) {
            parser.setMinSize(parseSize(minSize));
        }

        return com.duplicatefinder.DuplicateFinderApp.run(parser);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Parses human-friendly size strings (e.g., "1MB", "500KB") to bytes. */
    private long parseSize(String s) {
        String upper = s.toUpperCase().trim();
        if (upper.endsWith("GB")) return Long.parseLong(upper.replace("GB","").trim()) * 1024L * 1024 * 1024;
        if (upper.endsWith("MB")) return Long.parseLong(upper.replace("MB","").trim()) * 1024L * 1024;
        if (upper.endsWith("KB")) return Long.parseLong(upper.replace("KB","").trim()) * 1024L;
        if (upper.endsWith("B"))  return Long.parseLong(upper.replace("B","").trim());
        return Long.parseLong(upper);
    }

    /** Provides completion candidates for the --export option. */
    static class ExportFormatCandidates extends ArrayList<String> {
        ExportFormatCandidates() { super(List.of("json", "csv")); }
    }
}

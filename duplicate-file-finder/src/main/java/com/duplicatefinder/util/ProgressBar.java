package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Help.Ansi;

import java.util.function.Consumer;

/**
 * Phase-aware, ANSI-colourised console progress display.
 *
 * <h2>Design Goals</h2>
 * <ul>
 *   <li><b>Phase awareness</b> — each of the three scan phases has a distinct visual style,
 *       so the user always knows which phase is active and how far along it is.</li>
 *   <li><b>Dynamic label updates</b> — the label and total are updated atomically via
 *       {@link #setPhase(Phase, int)}, allowing the caller to switch phases without
 *       recreating the object.</li>
 *   <li><b>ANSI graceful degradation</b> — picocli's {@code Ansi.AUTO} detects whether
 *       the attached terminal supports ANSI escape codes.  On Windows CMD or in CI pipelines
 *       without a TTY, it strips the escape codes automatically.</li>
 * </ul>
 *
 * <h2>Phase Descriptions</h2>
 * <ul>
 *   <li>{@link Phase#SCANNING} — "Scanning directories..." with a filled progress bar.
 *       Total is the number of files discovered.</li>
 *   <li>{@link Phase#FILTERING} — "Filtering by size..." with a braille spinner.
 *       Total is irrelevant (the phase is sub-millisecond); a spinner indicates
 *       activity without implying a known duration.</li>
 *   <li>{@link Phase#HASHING} — "Computing checksums (X/Y files)..." with a filled bar.
 *       Total is the number of CRC32-candidate files.</li>
 *   <li>{@link Phase#DONE} — prints a final "✔" line and moves to a new line.</li>
 * </ul>
 */
public class ProgressBar {

    private static final Logger logger = LoggerFactory.getLogger(ProgressBar.class);

    /** Braille spinner frames — cycles smoothly through 8 positions. */
    private static final String[] SPINNER_FRAMES = { "⠋", "⠙", "⠸", "⠴", "⠦", "⠧", "⠇", "⠏" };

    /** Width of the filled bar segment in characters. */
    private static final int BAR_WIDTH = 40;

    /** Minimum milliseconds between display refreshes — avoids overwhelming stdout. */
    private static final long UPDATE_INTERVAL_MS = 80;

    // ── Phases ───────────────────────────────────────────────────────────────

    /**
     * Enumeration of scan phases.  Each phase drives a different visual style.
     */
    public enum Phase {
        SCANNING("Scanning directories"),
        FILTERING("Filtering by size"),
        HASHING("Computing checksums"),
        DONE("Done");

        final String label;
        Phase(String label) { this.label = label; }
    }

    // ── State (guarded by `this` monitor) ────────────────────────────────────

    private Phase  currentPhase  = Phase.SCANNING;
    private int    total         = 0;
    private int    current       = 0;
    private int    spinnerIndex  = 0;
    private long   lastUpdateMs  = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a ProgressBar starting in {@link Phase#SCANNING}.
     *
     * @param total    initial total (e.g., estimated file count for scanning)
     * @param taskName ignored — retained for API compatibility; use {@link Phase} labels instead
     */
    public ProgressBar(int total, String taskName) {
        this.total = total;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Switches to a new phase and resets the progress counter.
     *
     * @param phase    the phase to enter
     * @param newTotal the total work units for this phase (ignored for FILTERING/DONE)
     */
    public synchronized void setPhase(Phase phase, int newTotal) {
        this.currentPhase = phase;
        this.total        = newTotal;
        this.current      = 0;
        this.spinnerIndex = 0;

        if (phase == Phase.DONE) {
            printDone();
        } else {
            render();  // immediate render on phase transition
        }
    }

    /**
     * Increments the progress counter by {@code delta} and re-renders if the
     * update interval has elapsed.
     *
     * @param delta number of work units completed since last call
     */
    public synchronized void update(int delta) {
        current += delta;
        long now = System.currentTimeMillis();
        if (now - lastUpdateMs >= UPDATE_INTERVAL_MS || current >= total) {
            render();
            lastUpdateMs = now;
        }
    }

    /**
     * Sets the progress counter to an absolute value and re-renders immediately.
     *
     * @param value the new absolute progress value
     */
    public synchronized void setCurrent(int value) {
        this.current = value;
        render();
    }

    /**
     * Marks the current phase as complete (current = total) and re-renders.
     */
    public synchronized void complete() {
        current = total;
        render();
    }

    /**
     * Factory method: returns a {@link Consumer} that calls {@link #update(int)}
     * with the given value.  Convenience for use with lambda callbacks.
     */
    public Consumer<Integer> createProgressListener() {
        return count -> update(count);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders the appropriate widget for the current phase to stdout.
     *
     * <p>Uses {@code \r} (carriage return) to overwrite the current line in place,
     * giving the appearance of a live-updating display.  The final {@link #printDone()}
     * call emits a {@code \n} to advance to the next line.</p>
     *
     * <p>All ANSI escape sequences are emitted via {@code picocli.CommandLine.Help.Ansi.AUTO.string(...)}.
     * When the terminal does not support ANSI (e.g., Windows CMD without ANSI enabled,
     * or CI pipelines with {@code NO_COLOR} set), picocli strips the escape codes and
     * prints plain text — ensuring the output is always readable.</p>
     */
    private void render() {
        switch (currentPhase) {
            case SCANNING, HASHING -> renderBar();
            case FILTERING         -> renderSpinner();
            case DONE              -> printDone();
        }
    }

    /** Renders a classic filled progress bar for SCANNING and HASHING phases. */
    private void renderBar() {
        double pct    = (total <= 0) ? 100.0 : Math.min(100.0, (double) current / total * 100.0);
        int    filled = (int) (pct / 100.0 * BAR_WIDTH);
        int    empty  = BAR_WIDTH - filled;

        String bar = "=".repeat(Math.max(0, filled))
                   + (filled < BAR_WIDTH ? ">" : "")
                   + "-".repeat(Math.max(0, filled < BAR_WIDTH ? empty - 1 : empty));

        String label = (currentPhase == Phase.HASHING)
            ? String.format("Computing checksums (%d/%d files)", current, total)
            : currentPhase.label + "...";

        // @|cyan ...|@ wraps text in cyan ANSI colour; degrades to plain text automatically.
        String line = Ansi.AUTO.string(String.format(
            "\r@|cyan %s|@ [%s] @|bold,cyan %.1f%%|@",
            label, bar, pct));

        System.out.print(line);
        System.out.flush();
    }

    /** Renders a braille spinner for the FILTERING phase (duration is too short for a bar). */
    private void renderSpinner() {
        String frame = SPINNER_FRAMES[spinnerIndex % SPINNER_FRAMES.length];
        spinnerIndex++;

        String line = Ansi.AUTO.string(String.format(
            "\r@|cyan %s|@ %s ",
            currentPhase.label + "...", frame));

        System.out.print(line);
        System.out.flush();
    }

    /** Prints the completion line with a green tick and moves to the next line. */
    private void printDone() {
        String line = Ansi.AUTO.string("\r@|green ✔ All phases complete.|@                              \n");
        System.out.print(line);
        System.out.flush();
    }
}

package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple console progress bar utility.
 */
public class ProgressBar {
    private static final Logger logger = LoggerFactory.getLogger(ProgressBar.class);
    
    private final int total;
    private final int width;
    private final String taskName;
    private int current = 0;
    private long lastUpdate = 0;
    private static final long UPDATE_INTERVAL_MS = 100; // Update every 100ms
    
    public ProgressBar(int total, String taskName) {
        this(total, 50, taskName);
    }
    
    public ProgressBar(int total, int width, String taskName) {
        this.total = total;
        this.width = width;
        this.taskName = taskName;
    }
    
    /**
     * Updates the progress bar.
     * 
     * @param increment Amount to increment progress by
     */
    public synchronized void update(int increment) {
        current += increment;
        long now = System.currentTimeMillis();
        
        // Only update display if enough time has passed
        if (now - lastUpdate > UPDATE_INTERVAL_MS || current >= total) {
            display();
            lastUpdate = now;
        }
    }
    
    /**
     * Sets the current progress.
     * 
     * @param current Current progress value
     */
    public synchronized void setCurrent(int current) {
        this.current = current;
        display();
    }
    
    /**
     * Displays the progress bar.
     */
    private void display() {
        if (total <= 0) return;
        
        double percent = Math.min(100.0, (double) current / total * 100);
        int filled = (int) (percent / 100.0 * width);
        int empty = width - filled;
        
        StringBuilder bar = new StringBuilder();
        bar.append("\r"); // Move to beginning of line
        bar.append(taskName).append(": [");
        
        for (int i = 0; i < filled; i++) {
            bar.append("=");
        }
        
        if (filled < width) {
            bar.append(">");
            empty--;
        }
        
        for (int i = 0; i < empty; i++) {
            bar.append("-");
        }
        
        bar.append(String.format("] %.1f%% (%d/%d)", percent, current, total));
        
        System.out.print(bar.toString());
        System.out.flush();
        
        if (current >= total) {
            System.out.println(); // Move to next line when complete
        }
    }
    
    /**
     * Marks the progress as complete.
     */
    public synchronized void complete() {
        current = total;
        display();
    }
    
    /**
     * Creates a progress listener that can be used with DirectoryScanner and DuplicateDetector.
     * 
     * @return Progress listener consumer
     */
    public java.util.function.Consumer<Integer> createProgressListener() {
        return this::update;
    }
}

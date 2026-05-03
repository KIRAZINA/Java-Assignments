package minigit.model;

import minigit.util.Sha1Hasher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Represents a Git commit object.
 * Contains metadata about a commit including parent, tree, author, message, and timestamp.
 */
public class Commit {
    
    private final String treeHash;
    private final String parentHash;
    private final String author;
    private final String message;
    private final long timestamp;
    private final String hash;
    
    /**
     * Creates a new Commit.
     * 
     * @param treeHash hash of the tree object
     * @param parentHash hash of the parent commit (null for initial commit)
     * @param author author name and email
     * @param message commit message
     * @param timestamp Unix timestamp
     * @throws IllegalArgumentException if required fields are invalid
     */
    public Commit(String treeHash, String parentHash, String author, String message, long timestamp) {
        if (!Sha1Hasher.isValidHash(treeHash)) {
            throw new IllegalArgumentException("Invalid tree hash: " + treeHash);
        }
        if (parentHash != null && !Sha1Hasher.isValidHash(parentHash)) {
            throw new IllegalArgumentException("Invalid parent hash: " + parentHash);
        }
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("Author cannot be null or empty");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
        
        this.treeHash = treeHash.toLowerCase();
        this.parentHash = parentHash != null ? parentHash.toLowerCase() : null;
        this.author = author;
        this.message = message;
        this.timestamp = timestamp;
        this.hash = computeHash();
    }
    
    /**
     * Creates a Commit with current timestamp.
     */
    public Commit(String treeHash, String parentHash, String author, String message) {
        this(treeHash, parentHash, author, message, Instant.now().getEpochSecond());
    }
    
    /**
     * Parses a commit object from its content.
     * 
     * @param content the commit content bytes
     * @return parsed Commit object
     * @throws IllegalArgumentException if format is invalid
     */
    public static Commit parse(byte[] content) {
        String contentStr = new String(content, StandardCharsets.UTF_8);
        String[] lines = contentStr.split("\n");
        
        String treeHash = null;
        String parentHash = null;
        String author = null;
        StringBuilder message = new StringBuilder();
        boolean inMessage = false;
        
        for (String line : lines) {
            if (inMessage) {
                if (message.length() > 0) {
                    message.append("\n");
                }
                message.append(line);
            } else if (line.startsWith("tree ")) {
                treeHash = line.substring(5);
            } else if (line.startsWith("parent ")) {
                parentHash = line.substring(7);
            } else if (line.startsWith("author ")) {
                author = line.substring(7);
            } else if (line.isEmpty()) {
                inMessage = true;
            }
        }
        
        if (treeHash == null) {
            throw new IllegalArgumentException("Missing tree hash in commit");
        }
        if (author == null) {
            throw new IllegalArgumentException("Missing author in commit");
        }
        
        // Extract timestamp from author line (format: "name <email> timestamp timezone")
        long timestamp = Instant.now().getEpochSecond();
        if (author.contains(" ")) {
            String[] parts = author.split(" ");
            if (parts.length >= 3) {
                try {
                    timestamp = Long.parseLong(parts[parts.length - 2]);
                } catch (NumberFormatException e) {
                    // Keep default timestamp
                }
            }
        }
        
        return new Commit(treeHash, parentHash, author, message.toString(), timestamp);
    }
    
    /**
     * Serializes the commit to Git commit format.
     * 
     * @return serialized byte array
     */
    public byte[] serialize() {
        StringBuilder content = new StringBuilder();
        content.append("tree ").append(treeHash).append("\n");
        
        if (parentHash != null) {
            content.append("parent ").append(parentHash).append("\n");
        }
        
        content.append("author ").append(author).append("\n");
        content.append("\n");
        content.append(message);
        
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Gets the tree hash.
     * 
     * @return the tree hash
     */
    public String getTreeHash() {
        return treeHash;
    }
    
    /**
     * Gets the parent hash.
     * 
     * @return the parent hash, or null for initial commit
     */
    public String getParentHash() {
        return parentHash;
    }
    
    /**
     * Gets the author.
     * 
     * @return the author string
     */
    public String getAuthor() {
        return author;
    }
    
    /**
     * Gets the commit message.
     * 
     * @return the commit message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Gets the timestamp.
     * 
     * @return the Unix timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the formatted timestamp.
     * 
     * @return formatted timestamp string
     */
    public String getFormattedTimestamp() {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(timestamp));
    }
    
    /**
     * Checks if this is an initial commit (no parent).
     * 
     * @return true if this is an initial commit
     */
    public boolean isInitialCommit() {
        return parentHash == null;
    }
    
    /**
     * Computes the SHA-1 hash of the commit object.
     * 
     * @return the hash string
     */
    private String computeHash() {
        GitObject commitObject = new GitObject(ObjectType.COMMIT, serialize());
        return commitObject.getHash();
    }
    
    /**
     * Gets the commit hash.
     * 
     * @return the SHA-1 hash
     */
    public String getHash() {
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Commit that = (Commit) obj;
        return hash.equals(that.hash);
    }
    
    @Override
    public int hashCode() {
        return hash.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("Commit{hash=%s, tree=%s, parent=%s, author=%s, message=%s}", 
                           hash.substring(0, 7), treeHash.substring(0, 7), 
                           parentHash != null ? parentHash.substring(0, 7) : "null",
                           author, message.split("\n")[0]);
    }
}

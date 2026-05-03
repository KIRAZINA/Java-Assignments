package minigit.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for safe path operations.
 * Provides methods to join paths safely and ensure directory existence.
 */
public class PathUtils {
    
    /**
     * Safely joins multiple path segments.
     * Prevents directory traversal attacks by normalizing the path.
     * 
     * @param base the base path
     * @param segments additional path segments
     * @return normalized path
     * @throws IllegalArgumentException if path traversal is detected
     */
    public static Path safeJoin(String base, String... segments) {
        Path result = Paths.get(base);
        
        for (String segment : segments) {
            if (segment.contains("..") || segment.contains("~")) {
                throw new IllegalArgumentException("Path traversal detected in segment: " + segment);
            }
            result = result.resolve(segment);
        }
        
        return result.normalize();
    }
    
    /**
     * Ensures that a directory exists, creating it if necessary.
     * 
     * @param directory the directory path
     * @throws RuntimeException if directory cannot be created
     */
    public static void ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + directory, e);
        }
    }
    
    /**
     * Ensures that parent directories of a file path exist.
     * 
     * @param filePath the file path
     * @throws RuntimeException if parent directories cannot be created
     */
    public static void ensureParentExists(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }
    }
    
    /**
     * Gets the object file path based on Git's object storage scheme.
     * Objects are stored as .mini-git/objects/ab/cdef1234...
     * 
     * @param gitRoot the root directory of the git repository
     * @param hash the SHA-1 hash of the object
     * @return path where the object should be stored
     * @throws IllegalArgumentException if hash is invalid
     */
    public static Path getObjectPath(Path gitRoot, String hash) {
        if (!Sha1Hasher.isValidHash(hash)) {
            throw new IllegalArgumentException("Invalid SHA-1 hash: " + hash);
        }
        
        String dir = hash.substring(0, 2);
        String filename = hash.substring(2);
        
        return safeJoin(gitRoot.toString(), ".mini-git", "objects", dir, filename);
    }
    
    /**
     * Gets the reference file path.
     * References are stored as .mini-git/refs/heads/<name> or .mini-git/HEAD
     * 
     * @param gitRoot the root directory of the git repository
     * @param refName the name of the reference (e.g., "heads/main" or "HEAD")
     * @return path where the reference should be stored
     */
    public static Path getRefPath(Path gitRoot, String refName) {
        if (refName.equals("HEAD")) {
            return safeJoin(gitRoot.toString(), ".mini-git", "HEAD");
        } else {
            return safeJoin(gitRoot.toString(), ".mini-git", "refs", refName);
        }
    }
    
    /**
     * Validates reference name according to Git's naming rules.
     * Simplified validation: allows alphanumeric characters, underscores, hyphens, and forward slashes.
     * 
     * @param refName the reference name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidRefName(String refName) {
        if (refName == null || refName.isEmpty()) {
            return false;
        }
        
        // Simplified validation for the project scope
        return refName.matches("[a-zA-Z0-9_/.-]+") && !refName.contains("..");
    }
}

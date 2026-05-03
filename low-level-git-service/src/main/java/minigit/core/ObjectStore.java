package minigit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import minigit.model.GitObject;
import minigit.util.PathUtils;
import minigit.util.Sha1Hasher;

/**
 * Handles storage and retrieval of Git objects using content-addressable storage.
 * Objects are stored in .mini-git/objects/ab/cdef1234... format.
 */
public class ObjectStore {
    
    private final Path gitRoot;
    private final Path objectsDir;
    
    /**
     * Creates an ObjectStore for the given repository.
     * 
     * @param gitRoot the root directory of the git repository
     */
    public ObjectStore(Path gitRoot) {
        this.gitRoot = gitRoot;
        this.objectsDir = gitRoot.resolve(".mini-git").resolve("objects");
    }
    
    /**
     * Stores a Git object in the object store.
     * 
     * @param object the Git object to store
     * @throws RuntimeException if storage fails
     */
    public void store(GitObject object) {
        String hash = object.getHash();
        Path objectPath = PathUtils.getObjectPath(gitRoot, hash);
        
        // Ensure parent directory exists (idempotent, safe for concurrent use)
        PathUtils.ensureParentExists(objectPath);
        
        byte[] serializedData = object.serialize();
        
        try {
            // Fix #9: if object already exists, verify content matches (detect hash collisions).
            if (Files.exists(objectPath)) {
                byte[] existing = Files.readAllBytes(objectPath);
                if (!Arrays.equals(existing, serializedData)) {
                    // Same hash but different content — SHA-1 collision
                    throw new HashCollisionException(
                        "Hash collision detected for object: " + hash);
                }
                return; // Object already stored and identical — nothing to do
            }
            
            // Fix #7: atomic write via temp-file + move to avoid TOCTOU race condition.
            // Fix §1.2: temp file is cleaned up in finally so it never lingers on disk
            // even if Files.write() or Files.move() throws IOException.
            Path tempPath = objectPath.getParent()
                    .resolve(objectPath.getFileName().toString() + ".tmp");
            try {
                Files.write(tempPath, serializedData);
                Files.move(tempPath, objectPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } finally {
                // Silently ignore errors — if the move succeeded the file is gone already.
                try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
            }
        } catch (HashCollisionException e) {
            throw e; // re-throw without wrapping
        } catch (IOException e) {
            throw new RuntimeException("Failed to store object " + hash, e);
        }
    }
    
    /**
     * Retrieves a Git object by its hash.
     * Fix §3.1: after parsing, recomputes the SHA-1 from the stored bytes and
     * compares it with the requested hash. If they differ the object is corrupt
     * (on-disk tampering or filesystem error) and a RuntimeException is thrown.
     *
     * @param hash the SHA-1 hash of the object
     * @return the Git object, or null if not found
     * @throws RuntimeException if retrieval or integrity check fails
     */
    public GitObject retrieve(String hash) {
        if (!Sha1Hasher.isValidHash(hash)) {
            return null;
        }
        
        Path objectPath = PathUtils.getObjectPath(gitRoot, hash);
        if (!objectPath.toFile().exists()) {
            return null;
        }
        
        try {
            byte[] data = Files.readAllBytes(objectPath);
            GitObject parsed = GitObject.parse(data);
            
            // Fix §3.1: verify the recomputed hash matches what we asked for.
            // A mismatch means the file was corrupted or tampered with on disk.
            if (!parsed.getHash().equals(hash)) {
                throw new RuntimeException(
                    "Object corruption detected: requested hash=" + hash
                    + ", recomputed hash=" + parsed.getHash());
            }
            
            return parsed;
        } catch (RuntimeException e) {
            throw e; // preserve RuntimeException (incl. corruption) as-is
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve object " + hash, e);
        }
    }
    
    /**
     * Checks if an object exists in the store.
     * 
     * @param hash the SHA-1 hash of the object
     * @return true if the object exists, false otherwise
     */
    public boolean exists(String hash) {
        if (!Sha1Hasher.isValidHash(hash)) {
            return false;
        }
        return PathUtils.getObjectPath(gitRoot, hash).toFile().exists();
    }
    
    /**
     * Deletes an object from the store.
     * 
     * @param hash the SHA-1 hash of the object to delete
     * @return true if the object was deleted, false if it didn't exist
     * @throws RuntimeException if deletion fails
     */
    public boolean delete(String hash) {
        if (!Sha1Hasher.isValidHash(hash)) {
            return false;
        }
        
        Path objectPath = PathUtils.getObjectPath(gitRoot, hash);
        
        try {
            return Files.deleteIfExists(objectPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete object " + hash, e);
        }
    }
    
    /**
     * Gets the raw bytes of an object.
     * 
     * @param hash the SHA-1 hash of the object
     * @return the raw object bytes, or null if not found
     * @throws RuntimeException if reading fails
     */
    public byte[] getRawBytes(String hash) {
        if (!Sha1Hasher.isValidHash(hash)) {
            return null;
        }
        
        Path objectPath = PathUtils.getObjectPath(gitRoot, hash);
        
        if (!Files.exists(objectPath)) {
            return null;
        }
        
        try {
            return Files.readAllBytes(objectPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read raw bytes for object " + hash, e);
        }
    }
    
    /**
     * Stores raw object bytes and returns the hash.
     * 
     * @param data the raw object bytes
     * @return the SHA-1 hash of the stored object
     * @throws RuntimeException if storage fails
     */
    public String storeRaw(byte[] data) {
        GitObject object = GitObject.parse(data);
        store(object);
        return object.getHash();
    }
    
    /**
     * Initializes the object store directory structure.
     * 
     * @throws RuntimeException if initialization fails
     */
    public void initialize() {
        PathUtils.ensureDirectoryExists(objectsDir);
    }
    
    /**
     * Gets the total number of objects in the store.
     * 
     * @return the number of stored objects
     */
    public int getObjectCount() {
        if (!Files.exists(objectsDir)) {
            return 0;
        }
        
        try {
            return (int) Files.walk(objectsDir)
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count objects", e);
        }
    }
    
    /**
     * Validates integrity of all stored objects. Checks:
     * <ol>
     *   <li><b>Format:</b> parses as a valid GitObject (type SP size NUL content).</li>
     *   <li><b>Structural integrity:</b> recomputes SHA-1 from the parsed content and
     *       compares it with the 2-char directory + 38-char filename that forms the
     *       expected hash on the filesystem.</li>
     * </ol>
     *
     * @return count of objects failing either check
     */
    public int validateIntegrity() {
        if (!Files.exists(objectsDir)) {
            return 0;
        }
        
        // Fix #10: count ALL invalid objects, not just the first one.
        AtomicInteger invalidCount = new AtomicInteger(0);
        
        try {
            Files.walk(objectsDir)
                    .filter(Files::isRegularFile)
                    .forEach(objectPath -> {
                        try {
                            byte[] data = Files.readAllBytes(objectPath);
                            GitObject parsed = GitObject.parse(data);
                            // Verify stored hash matches file path (structural integrity)
                            String expectedHash =
                                    objectPath.getParent().getFileName().toString()
                                    + objectPath.getFileName().toString();
                            if (!parsed.getHash().equals(expectedHash)) {
                                invalidCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            invalidCount.incrementAndGet();
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate object store", e);
        }
        
        return invalidCount.get();
    }
}

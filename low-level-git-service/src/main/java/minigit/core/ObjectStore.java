package minigit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        
        // Ensure parent directory exists
        PathUtils.ensureParentExists(objectPath);
        
        try {
            // Check if object already exists
            if (Files.exists(objectPath)) {
                return; // Object already stored
            }
            
            byte[] serializedData = object.serialize();
            Files.write(objectPath, serializedData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store object " + hash, e);
        }
    }
    
    /**
     * Retrieves a Git object by its hash.
     * 
     * @param hash the SHA-1 hash of the object
     * @return the Git object, or null if not found
     * @throws RuntimeException if retrieval fails
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
            return GitObject.parse(data);
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
     * Validates the object store integrity.
     * Checks that all stored objects have valid hashes.
     * 
     * @return the number of invalid objects found
     */
    public int validateIntegrity() {
        if (!Files.exists(objectsDir)) {
            return 0;
        }
        
        int invalidCount = 0;
        
        try {
            Files.walk(objectsDir)
                    .filter(Files::isRegularFile)
                    .forEach(objectPath -> {
                        try {
                            byte[] data = Files.readAllBytes(objectPath);
                            GitObject.parse(data);
                        } catch (Exception e) {
                            // Invalid object found
                            throw new RuntimeException("Invalid object: " + objectPath, e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getMessage().startsWith("Invalid object:")) {
                return 1; // At least one invalid object found
            }
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate object store", e);
        }
        
        return invalidCount;
    }
}

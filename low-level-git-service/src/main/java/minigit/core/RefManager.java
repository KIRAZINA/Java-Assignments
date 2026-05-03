package minigit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import minigit.model.Ref;
import minigit.util.PathUtils;

/**
 * Manages Git references (refs) including HEAD and branch references.
 * References are stored as plain text files in .mini-git/refs/ and .mini-git/HEAD.
 */
public class RefManager {
    
    private final Path gitRoot;
    private final Path refsDir;
    private final Path headPath;
    
    /**
     * Creates a RefManager for the given repository.
     * 
     * @param gitRoot the root directory of the git repository
     */
    public RefManager(Path gitRoot) {
        this.gitRoot = gitRoot;
        this.refsDir = gitRoot.resolve(".mini-git").resolve("refs");
        this.headPath = gitRoot.resolve(".mini-git").resolve("HEAD");
    }
    
    /**
     * Stores a reference.
     * 
     * @param ref the reference to store
     * @throws RuntimeException if storage fails
     */
    public void storeRef(Ref ref) {
        String normalizedName = normalizeRefName(ref.getName());
        if (!PathUtils.isValidRefName(normalizedName)) {
            throw new IllegalArgumentException("Invalid ref name: " + ref.getName());
        }
        
        Path refPath = PathUtils.getRefPath(gitRoot, normalizedName);
        PathUtils.ensureParentExists(refPath);
        
        try {
            String content = ref.serialize();
            Files.write(refPath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to store ref: " + normalizedName, e);
        }
    }
    
    /**
     * Retrieves a reference by name.
     * 
     * @param name the reference name (e.g., "heads/main", "refs/heads/main", or "HEAD")
     * @return the Ref object, or null if not found
     * @throws RuntimeException if retrieval fails
     */
    public Ref getRef(String name) {
        String normalizedName = normalizeRefName(name);
        Path refPath = PathUtils.getRefPath(gitRoot, normalizedName);
        
        if (!Files.exists(refPath)) {
            return null;
        }
        
        try {
            String content = Files.readString(refPath, java.nio.charset.StandardCharsets.UTF_8);
            content = content.trim();
            return Ref.parse(normalizedName, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ref: " + name, e);
        }
    }
    
    /**
     * Normalizes a reference name by removing "refs/" prefix if present.
     * 
     * @param name the reference name
     * @return normalized reference name
     */
    private String normalizeRefName(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith("refs/")) {
            return name.substring("refs/".length());
        }
        return name;
    }
    
    /**
     * Deletes a reference.
     * 
     * @param name the reference name
     * @return true if the reference was deleted, false if it didn't exist
     * @throws RuntimeException if deletion fails
     */
    public boolean deleteRef(String name) {
        if (name.equals("HEAD")) {
            throw new IllegalArgumentException("Cannot delete HEAD reference");
        }
        
        String normalizedName = normalizeRefName(name);
        Path refPath = PathUtils.getRefPath(gitRoot, normalizedName);
        
        try {
            return Files.deleteIfExists(refPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete ref: " + normalizedName, e);
        }
    }
    
    /**
     * Lists all references in the repository.
     * 
     * @return list of all references
     * @throws RuntimeException if listing fails
     */
    public List<Ref> listRefs() {
        List<Ref> refs = new ArrayList<>();
        
        // Add HEAD
        Ref headRef = getRef("HEAD");
        if (headRef != null) {
            refs.add(headRef);
        }
        
        // Add all refs under .mini-git/refs/
        if (Files.exists(refsDir)) {
            try {
                Files.walk(refsDir)
                        .filter(Files::isRegularFile)
                        .forEach(refPath -> {
                            String relativePath = refsDir.relativize(refPath).toString();
                            String refName = relativePath.replace('\\', '/'); // Normalize path separators
                            Ref ref = getRef(refName);
                            if (ref != null) {
                                refs.add(ref);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to list refs", e);
            }
        }
        
        return refs;
    }
    
    /**
     * Gets the current HEAD reference.
     * 
     * @return the HEAD reference, or null if not set
     */
    public Ref getHead() {
        return getRef("HEAD");
    }
    
    /**
     * Sets the HEAD reference.
     * 
     * @param target the target (hash or symbolic ref)
     * @param symbolic true if symbolic, false if direct
     * @throws RuntimeException if setting fails
     */
    public void setHead(String target, boolean symbolic) {
        Ref headRef = new Ref("HEAD", target, symbolic);
        storeRef(headRef);
    }
    
    /**
     * Sets HEAD to point directly to a commit hash.
     * 
     * @param commitHash the commit hash
     */
    public void setHead(String commitHash) {
        setHead(commitHash, false);
    }
    
    /**
     * Resolves a reference chain to get the final commit hash.
     * Follows symbolic references until reaching a direct reference.
     * 
     * @param refName the reference name to resolve
     * @return the final commit hash, or null if not found or chain is broken
     */
    public String resolveRef(String refName) {
        Ref ref = getRef(refName);
        if (ref == null) {
            return null;
        }
        
        return resolveRef(ref, new HashSet<>());
    }
    
    /**
     * Resolves a reference chain to get the final commit hash.
     * 
     * @param ref the reference to resolve
     * @return the final commit hash, or null if chain is broken
     */
    public String resolveRef(Ref ref) {
        return resolveRef(ref, new HashSet<>());
    }
    
    /**
     * Internal resolver that tracks visited ref names to detect cycles.
     * Fix #8: prevents StackOverflowError caused by circular symbolic references.
     * 
     * @param ref     the reference to resolve
     * @param visited set of already-visited ref names in this chain
     * @return the final commit hash, or null if chain is broken or cyclic
     */
    private String resolveRef(Ref ref, Set<String> visited) {
        if (ref.isDirect()) {
            return ref.getTarget();
        }
        
        // Detect cycle: if we have already visited this ref name, bail out
        if (!visited.add(ref.getName())) {
            return null; // Circular reference detected
        }
        
        // Follow symbolic reference
        Ref targetRef = getRef(ref.getTarget());
        if (targetRef == null) {
            return null; // Broken symbolic reference
        }
        
        return resolveRef(targetRef, visited);
    }
    
    /**
     * Gets the current commit hash pointed to by HEAD.
     * 
     * @return the current commit hash, or null if HEAD is not set
     */
    public String getCurrentCommit() {
        return resolveRef("HEAD");
    }
    
    /**
     * Initializes the reference directory structure.
     * 
     * @throws RuntimeException if initialization fails
     */
    public void initialize() {
        PathUtils.ensureDirectoryExists(refsDir);
        
        // Create default HEAD pointing to main branch
        PathUtils.ensureDirectoryExists(refsDir.resolve("heads"));
        Ref defaultHead = Ref.symbolic("HEAD", "refs/heads/main");
        storeRef(defaultHead);
    }
    
    /**
     * Validates reference integrity.
     * Checks that all symbolic references resolve to valid targets.
     * 
     * @return the number of broken references found
     */
    public int validateIntegrity() {
        List<Ref> refs = listRefs();
        int brokenCount = 0;
        
        for (Ref ref : refs) {
            if (ref.isSymbolic()) {
                String resolved = resolveRef(ref);
                if (resolved == null) {
                    if (!ref.getName().equals("HEAD")) {
                        brokenCount++;
                    }
                }
            }
        }
        
        return brokenCount;
    }
}

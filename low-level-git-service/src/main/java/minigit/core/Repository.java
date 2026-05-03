package minigit.core;

import minigit.model.Commit;
import minigit.model.GitObject;
import minigit.model.ObjectType;
import minigit.model.Ref;
import minigit.model.TreeEntry;
import minigit.util.PathUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level repository operations.
 * Provides composite operations like initialization, commits, and repository management.
 */
public class Repository {
    
    private final Path gitRoot;
    private final ObjectStore objectStore;
    private final RefManager refManager;
    
    /**
     * Creates a Repository for the given directory.
     * 
     * @param gitRoot the root directory of the repository
     */
    public Repository(Path gitRoot) {
        this.gitRoot = gitRoot;
        this.objectStore = new ObjectStore(gitRoot);
        this.refManager = new RefManager(gitRoot);
    }
    
    /**
     * Initializes a new Git repository.
     * Creates the .mini-git directory structure and initial references.
     * 
     * @throws RuntimeException if initialization fails
     */
    public void initialize() {
        // Create .mini-git directory
        Path miniGitDir = gitRoot.resolve(".mini-git");
        PathUtils.ensureDirectoryExists(miniGitDir);
        
        // Initialize object store and ref manager
        objectStore.initialize();
        refManager.initialize();
    }
    
    /**
     * Checks if this is a valid Git repository.
     * 
     * @return true if .mini-git directory exists
     */
    public boolean isRepository() {
        return gitRoot.resolve(".mini-git").toFile().exists();
    }
    
    /**
     * Creates a commit object and updates references.
     * 
     * @param treeHash hash of the tree object
     * @param parentHash hash of the parent commit (null for initial commit)
     * @param author author information
     * @param message commit message
     * @return the created commit's hash
     * @throws RuntimeException if commit creation fails
     */
    public String createCommit(String treeHash, String parentHash, String author, String message) {
        Commit commit = new Commit(treeHash, parentHash, author, message);
        GitObject commitObject = new GitObject(ObjectType.COMMIT, commit.serialize());
        
        // Store the commit object
        objectStore.store(commitObject);
        
        // Update HEAD to point to the new commit
        refManager.setHead(commit.getHash());
        
        return commit.getHash();
    }
    
    /**
     * Creates an initial commit (no parent).
     * 
     * @param treeHash hash of the tree object
     * @param author author information
     * @param message commit message
     * @return the created commit's hash
     */
    public String createInitialCommit(String treeHash, String author, String message) {
        return createCommit(treeHash, null, author, message);
    }
    
    /**
     * Creates a tree object from the given entries.
     * 
     * @param entries list of tree entries
     * @return the created tree's hash
     * @throws RuntimeException if tree creation fails
     */
    public String createTree(List<TreeEntry> entries) {
        // Serialize all tree entries
        java.io.ByteArrayOutputStream treeData = new java.io.ByteArrayOutputStream();
        
        try {
            // Sort entries by name for consistent ordering
            List<TreeEntry> sortedEntries = new ArrayList<>(entries);
            sortedEntries.sort((a, b) -> a.getName().compareTo(b.getName()));
            
            for (TreeEntry entry : sortedEntries) {
                treeData.write(entry.serialize());
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to serialize tree", e);
        }
        
        GitObject treeObject = new GitObject(ObjectType.TREE, treeData.toByteArray());
        objectStore.store(treeObject);
        
        return treeObject.getHash();
    }
    
    /**
     * Creates a blob object from the given content.
     * 
     * @param content the file content
     * @return the created blob's hash
     * @throws RuntimeException if blob creation fails
     */
    public String createBlob(byte[] content) {
        GitObject blobObject = new GitObject(ObjectType.BLOB, content);
        objectStore.store(blobObject);
        return blobObject.getHash();
    }
    
    /**
     * Creates a blob object from a string.
     * 
     * @param content the file content as string
     * @return the created blob's hash
     */
    public String createBlob(String content) {
        return createBlob(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    /**
     * Retrieves a commit by its hash.
     * 
     * @param hash the commit hash
     * @return the Commit object, or null if not found
     */
    public Commit getCommit(String hash) {
        GitObject object = objectStore.retrieve(hash);
        if (object == null || object.getType() != ObjectType.COMMIT) {
            return null;
        }
        
        return Commit.parse(object.getContent());
    }
    
    /**
     * Retrieves the current commit (HEAD).
     * 
     * @return the current Commit, or null if no commits
     */
    public Commit getCurrentCommit() {
        String headHash = refManager.getCurrentCommit();
        if (headHash == null) {
            return null;
        }
        
        return getCommit(headHash);
    }
    
    /**
     * Gets the commit history starting from a given commit.
     * 
     * @param startHash the starting commit hash
     * @return list of commits in chronological order (oldest first)
     */
    public List<Commit> getCommitHistory(String startHash) {
        List<Commit> history = new ArrayList<>();
        String currentHash = startHash;
        
        while (currentHash != null) {
            Commit commit = getCommit(currentHash);
            if (commit == null) {
                break; // Broken history
            }
            
            history.add(commit);
            currentHash = commit.getParentHash();
        }
        
        // Reverse to get chronological order
        java.util.Collections.reverse(history);
        return history;
    }
    
    /**
     * Gets the commit history from HEAD.
     * 
     * @return list of commits in chronological order
     */
    public List<Commit> getCommitHistory() {
        String headHash = refManager.getCurrentCommit();
        if (headHash == null) {
            return new ArrayList<>();
        }
        
        return getCommitHistory(headHash);
    }
    
    /**
     * Creates or updates a branch reference.
     * 
     * @param branchName the branch name (e.g., "main", "feature/test")
     * @param commitHash the commit hash to point to
     * @throws RuntimeException if branch creation fails
     */
    public void createBranch(String branchName, String commitHash) {
        String refName = "heads/" + branchName;
        Ref branchRef = Ref.direct(refName, commitHash);
        refManager.storeRef(branchRef);
    }
    
    /**
     * Gets the commit hash for a branch.
     * 
     * @param branchName the branch name
     * @return the commit hash, or null if branch doesn't exist
     */
    public String getBranchHead(String branchName) {
        String refName = "heads/" + branchName;
        return refManager.resolveRef(refName);
    }
    
    /**
     * Lists all branches.
     * 
     * @return list of branch names
     */
    public List<String> listBranches() {
        List<String> branches = new ArrayList<>();
        List<Ref> refs = refManager.listRefs();
        
        for (Ref ref : refs) {
            if (ref.getName().startsWith("heads/")) {
                branches.add(ref.getName().substring(7)); // Remove "heads/" prefix
            }
        }
        
        return branches;
    }
    
    /**
     * Gets repository statistics.
     * 
     * @return formatted statistics string
     */
    public String getStatistics() {
        int objectCount = objectStore.getObjectCount();
        int commitCount = getCommitHistory().size();
        int branchCount = listBranches().size();
        
        return String.format("Objects: %d, Commits: %d, Branches: %d", 
                           objectCount, commitCount, branchCount);
    }
    
    /**
     * Validates repository integrity.
     * 
     * @return true if repository is valid, false otherwise
     */
    public boolean validateRepository() {
        if (!isRepository()) {
            return false;
        }
        
        try {
            int invalidObjects = objectStore.validateIntegrity();
            int brokenRefs = refManager.validateIntegrity();
            
            return invalidObjects == 0 && brokenRefs == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets the object store for direct access.
     * 
     * @return the ObjectStore instance
     */
    public ObjectStore getObjectStore() {
        return objectStore;
    }
    
    /**
     * Gets the reference manager for direct access.
     * 
     * @return the RefManager instance
     */
    public RefManager getRefManager() {
        return refManager;
    }
    
    /**
     * Gets the repository root directory.
     * 
     * @return the repository root path
     */
    public Path getGitRoot() {
        return gitRoot;
    }
}

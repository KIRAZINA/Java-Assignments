package minigit.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for PathUtils.
 */
public class PathUtilsTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    public void testSafeJoin() {
        Path result = PathUtils.safeJoin("/base", "subdir", "file.txt");
        assertTrue(result.toString().endsWith("base" + System.getProperty("file.separator") + "subdir" + System.getProperty("file.separator") + "file.txt"));
    }
    
    @Test
    public void testSafeJoinSingleSegment() {
        Path result = PathUtils.safeJoin("/base", "file.txt");
        assertTrue(result.toString().endsWith("base" + System.getProperty("file.separator") + "file.txt"));
    }
    
    @Test
    public void testSafeJoinWithTraversal() {
        assertThrows(IllegalArgumentException.class, () -> {
            PathUtils.safeJoin("/base", "..", "file.txt");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            PathUtils.safeJoin("/base", "subdir", "..", "file.txt");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            PathUtils.safeJoin("/base", "~", "file.txt");
        });
    }
    
    @Test
    public void testEnsureDirectoryExists() {
        Path newDir = tempDir.resolve("newdir");
        
        assertFalse(Files.exists(newDir));
        
        PathUtils.ensureDirectoryExists(newDir);
        
        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));
    }
    
    @Test
    public void testEnsureDirectoryExistsExisting() throws IOException {
        Path existingDir = tempDir.resolve("existing");
        Files.createDirectories(existingDir);
        
        assertTrue(Files.exists(existingDir));
        
        // Should not throw exception
        PathUtils.ensureDirectoryExists(existingDir);
        
        assertTrue(Files.exists(existingDir));
    }
    
    @Test
    public void testEnsureParentExists() {
        Path file = tempDir.resolve("subdir").resolve("file.txt");
        
        assertFalse(Files.exists(file.getParent()));
        
        PathUtils.ensureParentExists(file);
        
        assertTrue(Files.exists(file.getParent()));
        assertTrue(Files.isDirectory(file.getParent()));
    }
    
    @Test
    public void testEnsureParentExistsExistingParent() throws IOException {
        Path parent = tempDir.resolve("existing");
        Files.createDirectories(parent);
        Path file = parent.resolve("file.txt");
        
        assertTrue(Files.exists(parent));
        
        // Should not throw exception
        PathUtils.ensureParentExists(file);
        
        assertTrue(Files.exists(parent));
    }
    
    @Test
    public void testGetObjectPath() {
        Path gitRoot = tempDir.resolve("repo");
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        
        Path objectPath = PathUtils.getObjectPath(gitRoot, hash);
        
        String expected = gitRoot.resolve(".mini-git").resolve("objects")
                                .resolve("2a")
                                .resolve("ae6c35c94fcfb415dbe95f408b9ce91ee846ed")
                                .toString();
        assertEquals(expected, objectPath.toString());
    }
    
    @Test
    public void testGetObjectPathInvalidHash() {
        Path gitRoot = tempDir.resolve("repo");
        
        assertThrows(IllegalArgumentException.class, () -> {
            PathUtils.getObjectPath(gitRoot, "invalid");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            PathUtils.getObjectPath(gitRoot, "2aae6c35c94fcfb415dbe95f408b9ce91ee846e"); // 39 chars
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            PathUtils.getObjectPath(gitRoot, "2aae6c35c94fcfb415dbe95f408b9ce91ee846edd"); // 41 chars
        });
    }
    
    @Test
    public void testGetRefPath() {
        Path gitRoot = tempDir.resolve("repo");
        
        Path headPath = PathUtils.getRefPath(gitRoot, "HEAD");
        String expectedHead = gitRoot.resolve(".mini-git").resolve("HEAD").toString();
        assertEquals(expectedHead, headPath.toString());
        
        Path branchPath = PathUtils.getRefPath(gitRoot, "heads/main");
        String expectedBranch = gitRoot.resolve(".mini-git").resolve("refs").resolve("heads/main").toString();
        assertEquals(expectedBranch, branchPath.toString());
    }
    
    @Test
    public void testIsValidRefName() {
        assertTrue(PathUtils.isValidRefName("main"));
        assertTrue(PathUtils.isValidRefName("feature/test"));
        assertTrue(PathUtils.isValidRefName("bugfix-123"));
        assertTrue(PathUtils.isValidRefName("heads/main"));
        assertTrue(PathUtils.isValidRefName("refs/heads/main"));
        assertTrue(PathUtils.isValidRefName("feature_123"));
        assertTrue(PathUtils.isValidRefName("v1.0.0"));
        
        assertFalse(PathUtils.isValidRefName(null));
        assertFalse(PathUtils.isValidRefName(""));
        assertFalse(PathUtils.isValidRefName(" "));
        assertFalse(PathUtils.isValidRefName("ref with spaces"));
        assertFalse(PathUtils.isValidRefName("ref@with@symbols"));
        assertFalse(PathUtils.isValidRefName("ref:with:colons"));
        assertFalse(PathUtils.isValidRefName("ref..with..dots"));
    }
    
    @Test
    public void testSafeJoinNormalization() {
        Path result = PathUtils.safeJoin("/base/", "subdir/", "file.txt");
        assertTrue(result.toString().endsWith("base" + System.getProperty("file.separator") + "subdir" + System.getProperty("file.separator") + "file.txt"));
        
        // Test with relative paths that don't contain traversal
        Path result2 = PathUtils.safeJoin("/base", "./subdir", "file.txt");
        assertTrue(result2.toString().endsWith("base" + System.getProperty("file.separator") + "subdir" + System.getProperty("file.separator") + "file.txt"));
    }
}

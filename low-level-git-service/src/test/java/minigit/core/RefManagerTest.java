package minigit.core;

import minigit.model.Ref;
import minigit.util.Sha1Hasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Unit tests for RefManager.
 */
public class RefManagerTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    public void testStoreAndGetDirectRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        Ref ref = Ref.direct("heads/main", hash);
        
        refManager.storeRef(ref);
        
        Ref retrieved = refManager.getRef("heads/main");
        
        assertNotNull(retrieved);
        assertEquals("heads/main", retrieved.getName());
        assertEquals(hash, retrieved.getTarget());
        assertFalse(retrieved.isSymbolic());
        assertTrue(retrieved.isDirect());
    }
    
    @Test
    public void testStoreAndGetSymbolicRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        Ref ref = Ref.symbolic("HEAD", "refs/heads/main");
        
        refManager.storeRef(ref);
        
        Ref retrieved = refManager.getRef("HEAD");
        
        assertNotNull(retrieved);
        assertEquals("HEAD", retrieved.getName());
        assertEquals("refs/heads/main", retrieved.getTarget());
        assertTrue(retrieved.isSymbolic());
        assertFalse(retrieved.isDirect());
    }
    
    @Test
    public void testGetNonExistentRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        Ref retrieved = refManager.getRef("heads/nonexistent");
        
        assertNull(retrieved);
    }
    
    @Test
    public void testDeleteRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        Ref ref = Ref.direct("heads/test", hash);
        refManager.storeRef(ref);
        
        assertTrue(Files.exists(tempDir.resolve(".mini-git").resolve("refs").resolve("heads").resolve("test")));
        
        boolean deleted = refManager.deleteRef("heads/test");
        
        assertTrue(deleted);
        assertFalse(Files.exists(tempDir.resolve(".mini-git").resolve("refs").resolve("heads").resolve("test")));
        assertNull(refManager.getRef("heads/test"));
    }
    
    @Test
    public void testDeleteNonExistentRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        boolean deleted = refManager.deleteRef("heads/nonexistent");
        
        assertFalse(deleted);
    }
    
    @Test
    public void testDeleteHeadRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        assertThrows(IllegalArgumentException.class, () -> {
            refManager.deleteRef("HEAD");
        });
    }
    
    @Test
    public void testListRefs() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash1 = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        String hash2 = "3bbf7c46c94fcfb415dbe95f408b9ce91ee846ef";
        
        Ref mainRef = Ref.direct("heads/main", hash1);
        Ref featureRef = Ref.direct("heads/feature", hash2);
        Ref headRef = Ref.symbolic("HEAD", "refs/heads/main");
        
        refManager.storeRef(mainRef);
        refManager.storeRef(featureRef);
        refManager.storeRef(headRef);
        
        List<Ref> refs = refManager.listRefs();
        
        assertEquals(3, refs.size());
        
        boolean foundMain = false, foundFeature = false, foundHead = false;
        for (Ref ref : refs) {
            if (ref.getName().equals("heads/main")) foundMain = true;
            if (ref.getName().equals("heads/feature")) foundFeature = true;
            if (ref.getName().equals("HEAD")) foundHead = true;
        }
        
        assertTrue(foundMain);
        assertTrue(foundFeature);
        assertTrue(foundHead);
    }
    
    @Test
    public void testGetHead() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        Ref headRef = refManager.getHead();
        
        assertNotNull(headRef);
        assertEquals("HEAD", headRef.getName());
        assertTrue(headRef.isSymbolic());
        assertEquals("refs/heads/main", headRef.getTarget());
    }
    
    @Test
    public void testSetHead() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        
        refManager.setHead(hash);
        
        Ref headRef = refManager.getHead();
        assertNotNull(headRef);
        assertEquals(hash, headRef.getTarget());
        assertTrue(headRef.isDirect());
    }
    
    @Test
    public void testSetHeadSymbolic() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        refManager.setHead("refs/heads/feature", true);
        
        Ref headRef = refManager.getHead();
        assertNotNull(headRef);
        assertEquals("refs/heads/feature", headRef.getTarget());
        assertTrue(headRef.isSymbolic());
    }
    
    @Test
    public void testResolveDirectRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        Ref ref = Ref.direct("heads/main", hash);
        refManager.storeRef(ref);
        
        String resolved = refManager.resolveRef("heads/main");
        
        assertEquals(hash, resolved);
    }
    
    @Test
    public void testResolveSymbolicRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        Ref branchRef = Ref.direct("heads/main", hash);
        Ref headRef = Ref.symbolic("HEAD", "refs/heads/main");
        
        refManager.storeRef(branchRef);
        refManager.storeRef(headRef);
        
        String resolved = refManager.resolveRef("HEAD");
        
        assertEquals(hash, resolved);
    }
    
    @Test
    public void testResolveBrokenSymbolicRef() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        Ref headRef = Ref.symbolic("HEAD", "refs/heads/nonexistent");
        refManager.storeRef(headRef);
        
        String resolved = refManager.resolveRef("HEAD");
        
        assertNull(resolved);
    }
    
    @Test
    public void testGetCurrentCommit() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        
        // Initially no commit
        assertNull(refManager.getCurrentCommit());
        
        // Set HEAD to direct hash
        refManager.setHead(hash);
        assertEquals(hash, refManager.getCurrentCommit());
        
        // Set HEAD to symbolic ref pointing to hash
        Ref branchRef = Ref.direct("heads/main", hash);
        refManager.storeRef(branchRef);
        refManager.setHead("refs/heads/main", true);
        assertEquals(hash, refManager.getCurrentCommit());
    }
    
    @Test
    public void testValidateRefName() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        // Valid ref names
        assertDoesNotThrow(() -> {
            refManager.storeRef(Ref.direct("main", "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
        });
        
        assertDoesNotThrow(() -> {
            refManager.storeRef(Ref.direct("heads/feature", "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
        });
        
        // Invalid ref names
        assertThrows(IllegalArgumentException.class, () -> {
            refManager.storeRef(Ref.direct("", "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            refManager.storeRef(Ref.direct("ref with spaces", "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
        });
    }
    
    @Test
    public void testValidateIntegrity() {
        RefManager refManager = new RefManager(tempDir);
        refManager.initialize();
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        
        // Create valid refs
        Ref branchRef = Ref.direct("heads/main", hash);
        Ref headRef = Ref.symbolic("HEAD", "refs/heads/main");
        refManager.storeRef(branchRef);
        refManager.storeRef(headRef);
        
        int brokenRefs = refManager.validateIntegrity();
        assertEquals(0, brokenRefs);
        
        // Create broken symbolic ref
        Ref brokenRef = Ref.symbolic("refs/heads/broken", "refs/heads/nonexistent");
        refManager.storeRef(brokenRef);
        
        brokenRefs = refManager.validateIntegrity();
        assertEquals(1, brokenRefs);
    }
}

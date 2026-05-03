package minigit.core;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import minigit.model.GitObject;
import minigit.model.ObjectType;
import minigit.util.Sha1Hasher;

/**
 * Unit tests for ObjectStore.
 */
public class ObjectStoreTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    public void testStoreAndRetrieveBlob() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "Hello, World!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject blob = new GitObject(ObjectType.BLOB, content);
        
        store.store(blob);
        
        GitObject retrieved = store.retrieve(blob.getHash());
        
        assertNotNull(retrieved);
        assertEquals(blob.getHash(), retrieved.getHash());
        assertEquals(ObjectType.BLOB, retrieved.getType());
        assertArrayEquals(content, retrieved.getContent());
    }
    
    @Test
    public void testStoreAndRetrieveTree() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "100644 file.txt\0\1\2\3\4".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject tree = new GitObject(ObjectType.TREE, content);
        
        store.store(tree);
        
        GitObject retrieved = store.retrieve(tree.getHash());
        
        assertNotNull(retrieved);
        assertEquals(tree.getHash(), retrieved.getHash());
        assertEquals(ObjectType.TREE, retrieved.getType());
        assertArrayEquals(content, retrieved.getContent());
    }
    
    @Test
    public void testStoreAndRetrieveCommit() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "tree abc123\nparent def456\nauthor Test Author\n\nTest message".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject commit = new GitObject(ObjectType.COMMIT, content);
        
        store.store(commit);
        
        GitObject retrieved = store.retrieve(commit.getHash());
        
        assertNotNull(retrieved);
        assertEquals(commit.getHash(), retrieved.getHash());
        assertEquals(ObjectType.COMMIT, retrieved.getType());
        assertArrayEquals(content, retrieved.getContent());
    }
    
    @Test
    public void testExists() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "test content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject object = new GitObject(ObjectType.BLOB, content);
        
        assertFalse(store.exists(object.getHash()));
        
        store.store(object);
        
        assertTrue(store.exists(object.getHash()));
    }
    
    @Test
    public void testRetrieveNonExistent() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        GitObject retrieved = store.retrieve("0abcdef1234567890123456789012345678901234");
        
        assertNull(retrieved);
    }
    
    @Test
    public void testStoreRawBytes() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] rawData = "blob 13\0Hello, World!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = store.storeRaw(rawData);
        
        assertNotNull(hash);
        assertTrue(Sha1Hasher.isValidHash(hash));
        assertTrue(store.exists(hash));
        
        byte[] retrieved = store.getRawBytes(hash);
        assertArrayEquals(rawData, retrieved);
    }
    
    @Test
    public void testGetRawBytes() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "test content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject object = new GitObject(ObjectType.BLOB, content);
        store.store(object);
        
        byte[] rawBytes = store.getRawBytes(object.getHash());
        
        assertNotNull(rawBytes);
        GitObject parsed = GitObject.parse(rawBytes);
        assertEquals(object.getHash(), parsed.getHash());
        assertEquals(ObjectType.BLOB, parsed.getType());
        assertArrayEquals(content, parsed.getContent());
    }
    
    @Test
    public void testDelete() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "test content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject object = new GitObject(ObjectType.BLOB, content);
        store.store(object);
        
        assertTrue(store.exists(object.getHash()));
        
        boolean deleted = store.delete(object.getHash());
        
        assertTrue(deleted);
        assertFalse(store.exists(object.getHash()));
    }
    
    @Test
    public void testDeleteNonExistent() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        boolean deleted = store.delete("0abcdef1234567890123456789012345678901234");
        
        assertFalse(deleted);
    }
    
    @Test
    public void testGetObjectCount() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        assertEquals(0, store.getObjectCount());
        
        byte[] content1 = "content1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] content2 = "content2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        GitObject object1 = new GitObject(ObjectType.BLOB, content1);
        GitObject object2 = new GitObject(ObjectType.BLOB, content2);
        
        store.store(object1);
        assertEquals(1, store.getObjectCount());
        
        store.store(object2);
        assertEquals(2, store.getObjectCount());
        
        // Store the same object again - should not increase count
        store.store(object1);
        assertEquals(2, store.getObjectCount());
    }
    
    @Test
    public void testValidateIntegrity() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "valid content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject object = new GitObject(ObjectType.BLOB, content);
        store.store(object);
        
        int invalidCount = store.validateIntegrity();
        assertEquals(0, invalidCount);
    }
    
    @Test
    public void testObjectPathStructure() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "test content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject object = new GitObject(ObjectType.BLOB, content);
        store.store(object);
        
        String hash = object.getHash();
        String dir = hash.substring(0, 2);
        String filename = hash.substring(2);
        
        Path objectDir = tempDir.resolve(".mini-git").resolve("objects").resolve(dir);
        Path objectFile = objectDir.resolve(filename);
        
        assertTrue(Files.exists(objectDir));
        assertTrue(Files.exists(objectFile));
    }
    
    @Test
    public void testStoreSameObjectTwice() {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "test content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject object1 = new GitObject(ObjectType.BLOB, content);
        GitObject object2 = new GitObject(ObjectType.BLOB, content);
        
        store.store(object1);
        store.store(object2); // Same content, same hash
        
        assertEquals(1, store.getObjectCount());
        assertTrue(store.exists(object1.getHash()));
    }
    
    /**
     * Verifies that storing a different object that occupies the path of an already-stored
     * object (simulated by writing a tampered file) raises HashCollisionException.
     */
    @Test
    public void testHashCollisionThrows() throws Exception {
        ObjectStore store = new ObjectStore(tempDir);
        store.initialize();
        
        byte[] content = "collision content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitObject original = new GitObject(ObjectType.BLOB, content);
        store.store(original);
        
        // Tamper: overwrite the stored file with different serialized bytes so
        // the next store() call finds the path occupied but with different data.
        java.nio.file.Path objectPath = minigit.util.PathUtils.getObjectPath(tempDir, original.getHash());
        byte[] tampered = "blob 3\0abc".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(objectPath, tampered);
        
        // Trying to store the original object again should detect the mismatch.
        org.junit.jupiter.api.Assertions.assertThrows(
            HashCollisionException.class,
            () -> store.store(original)
        );
    }
}

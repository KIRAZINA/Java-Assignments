package minigit.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Sha1Hasher.
 */
public class Sha1HasherTest {
    
    @Test
    public void testHashString() {
        String input = "hello world";
        String hash = Sha1Hasher.hash(input);
        
        // Expected SHA-1 hash for "hello world"
        assertEquals("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed", hash);
    }
    
    @Test
    public void testHashBytes() {
        String input = "hello world";
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = Sha1Hasher.hash(bytes);
        
        assertEquals("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed", hash);
    }
    
    @Test
    public void testHashEmptyString() {
        String hash = Sha1Hasher.hash("");
        
        // Expected SHA-1 hash for empty string
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hash);
    }
    
    @Test
    public void testHashEmptyBytes() {
        String hash = Sha1Hasher.hash(new byte[0]);
        
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hash);
    }
    
    @Test
    public void testHashConsistency() {
        String input = "test input";
        String hash1 = Sha1Hasher.hash(input);
        String hash2 = Sha1Hasher.hash(input);
        
        assertEquals(hash1, hash2);
    }
    
    @Test
    public void testHashDifferentInputs() {
        String input1 = "input1";
        String input2 = "input2";
        
        String hash1 = Sha1Hasher.hash(input1);
        String hash2 = Sha1Hasher.hash(input2);
        
        assertNotEquals(hash1, hash2);
    }
    
    @Test
    public void testValidHash() {
        assertTrue(Sha1Hasher.isValidHash("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
        assertTrue(Sha1Hasher.isValidHash("0000000000000000000000000000000000000000"));
        assertTrue(Sha1Hasher.isValidHash("ffffffffffffffffffffffffffffffffffffffff"));
    }
    
    @Test
    public void testInvalidHash() {
        assertFalse(Sha1Hasher.isValidHash(null));
        assertFalse(Sha1Hasher.isValidHash(""));
        assertFalse(Sha1Hasher.isValidHash("short"));
        assertFalse(Sha1Hasher.isValidHash("2aae6c35c94fcfb415dbe95f408b9ce91ee846e")); // 39 chars
        assertFalse(Sha1Hasher.isValidHash("2aae6c35c94fcfb415dbe95f408b9ce91ee846edd")); // 41 chars
        assertFalse(Sha1Hasher.isValidHash("gaae6c35c94fcfb415dbe95f408b9ce91ee846ed")); // contains 'g'
        assertFalse(Sha1Hasher.isValidHash("2AAE6C35C94FCFB415DBE95F408B9CE91EE846ED")); // uppercase
        assertFalse(Sha1Hasher.isValidHash("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed ")); // trailing space
    }
    
    @Test
    public void testHashBinaryData() {
        byte[] binaryData = new byte[] {0x00, 0x01, 0x02, 0x03, (byte) 0xFF};
        String hash = Sha1Hasher.hash(binaryData);
        
        assertNotNull(hash);
        assertEquals(40, hash.length());
        assertTrue(Sha1Hasher.isValidHash(hash));
    }
    
    @Test
    public void testHashLargeData() {
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeInput.append("a");
        }
        
        String hash = Sha1Hasher.hash(largeInput.toString());
        
        assertNotNull(hash);
        assertEquals(40, hash.length());
        assertTrue(Sha1Hasher.isValidHash(hash));
    }
}

package minigit.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for SHA-1 hashing operations.
 * Provides methods to hash byte arrays and convert hashes to hex strings.
 */
public class Sha1Hasher {
    
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    
    /**
     * Computes SHA-1 hash of the given byte array.
     * 
     * @param data the byte array to hash
     * @return 40-character hex string representing the SHA-1 hash
     * @throws RuntimeException if SHA-1 algorithm is not available
     */
    public static String hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }
    
    /**
     * Computes SHA-1 hash of the given string using UTF-8 encoding.
     * 
     * @param data the string to hash
     * @return 40-character hex string representing the SHA-1 hash
     */
    public static String hash(String data) {
        return hash(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    /**
     * Converts byte array to hexadecimal string.
     * 
     * @param bytes the byte array to convert
     * @return hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    /**
     * Validates if a string is a valid SHA-1 hash (40 hex characters).
     * 
     * @param hash the string to validate
     * @return true if valid SHA-1 hash, false otherwise
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 40) {
            return false;
        }
        return hash.matches("[a-f0-9]{40}");
    }
}

package minigit.model;

import minigit.util.Sha1Hasher;

import java.nio.charset.StandardCharsets;

/**
 * Represents an entry in a Git tree object.
 * Each entry contains: mode, name, and hash.
 */
public class TreeEntry {
    
    private final String mode;
    private final String name;
    private final String hash;
    
    /**
     * Creates a new TreeEntry.
     * 
     * @param mode the file mode (e.g., "100644" for regular file, "040000" for directory)
     * @param name the entry name (filename or directory name)
     * @param hash the SHA-1 hash of the referenced object
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public TreeEntry(String mode, String name, String hash) {
        if (mode == null || mode.isEmpty()) {
            throw new IllegalArgumentException("Mode cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (!Sha1Hasher.isValidHash(hash)) {
            throw new IllegalArgumentException("Invalid hash: " + hash);
        }
        
        this.mode = mode;
        this.name = name;
        this.hash = hash.toLowerCase(); // Normalize to lowercase
    }
    
    /**
     * Parses a tree entry from its binary format.
     * Tree entries are stored as: "mode name\0hash"
     * 
     * @param data the binary data containing the entry
     * @param offset the starting offset in the data
     * @return TreeEntry object
     * @throws IllegalArgumentException if format is invalid
     */
    public static TreeEntry parse(byte[] data, int offset) {
        // Find the null separator
        int nullIndex = -1;
        for (int i = offset; i < data.length; i++) {
            if (data[i] == 0) {
                nullIndex = i;
                break;
            }
        }
        
        if (nullIndex == -1) {
            throw new IllegalArgumentException("Invalid tree entry format: missing null separator");
        }
        
        // Parse "mode name"
        String header = new String(data, offset, nullIndex - offset, StandardCharsets.UTF_8);
        int spaceIndex = header.indexOf(' ');
        
        if (spaceIndex == -1) {
            throw new IllegalArgumentException("Invalid tree entry format: missing space between mode and name");
        }
        
        String mode = header.substring(0, spaceIndex);
        String name = header.substring(spaceIndex + 1);
        
        // Parse hash (20 bytes after null)
        if (nullIndex + 20 > data.length) {
            throw new IllegalArgumentException("Invalid tree entry format: incomplete hash");
        }
        
        StringBuilder hashBuilder = new StringBuilder(40);
        for (int i = 0; i < 20; i++) {
            int b = data[nullIndex + 1 + i] & 0xFF;
            hashBuilder.append(String.format("%02x", b));
        }
        
        return new TreeEntry(mode, name, hashBuilder.toString());
    }
    
    /**
     * Serializes the tree entry to binary format.
     * Returns: "mode name\0hash"
     * 
     * @return serialized byte array
     */
    public byte[] serialize() {
        String header = mode + " " + name;
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        
        // Convert hex hash to bytes
        byte[] hashBytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            String hexByte = hash.substring(i * 2, i * 2 + 2);
            hashBytes[i] = (byte) Integer.parseInt(hexByte, 16);
        }
        
        byte[] result = new byte[headerBytes.length + 1 + hashBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        result[headerBytes.length] = 0; // null separator
        System.arraycopy(hashBytes, 0, result, headerBytes.length + 1, hashBytes.length);
        
        return result;
    }
    
    /**
     * Gets the file mode.
     * 
     * @return the mode string
     */
    public String getMode() {
        return mode;
    }
    
    /**
     * Gets the entry name.
     * 
     * @return the name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the object hash.
     * 
     * @return the SHA-1 hash
     */
    public String getHash() {
        return hash;
    }
    
    /**
     * Gets the total size of the serialized entry.
     * 
     * @return size in bytes
     */
    public int getSerializedSize() {
        return mode.length() + 1 + name.length() + 1 + 20; // mode + space + name + null + hash
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TreeEntry that = (TreeEntry) obj;
        return mode.equals(that.mode) && 
               name.equals(that.name) && 
               hash.equals(that.hash);
    }
    
    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + hash.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("TreeEntry{mode=%s, name=%s, hash=%s}", mode, name, hash);
    }
}

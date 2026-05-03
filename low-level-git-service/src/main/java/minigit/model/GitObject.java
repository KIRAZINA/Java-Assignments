package minigit.model;

import minigit.util.Sha1Hasher;

import java.nio.charset.StandardCharsets;

/**
 * Represents a Git object with type and content.
 * Git objects are stored in the format: "<type> <size>\0<content>"
 */
public class GitObject {
    
    private final ObjectType type;
    private final byte[] content;
    private final String hash;
    
    /**
     * Creates a new GitObject with the given type and content.
     * The hash is computed automatically from the object data.
     * 
     * @param type the object type (blob, tree, or commit)
     * @param content the raw content bytes
     */
    public GitObject(ObjectType type, byte[] content) {
        this.type = type;
        this.content = content.clone(); // Defensive copy
        this.hash = computeHash();
    }
    
    /**
     * Creates a GitObject by parsing raw Git object data.
     * The data should be in the format: "<type> <size>\0<content>"
     * 
     * @param rawData the raw object data
     * @return parsed GitObject
     * @throws IllegalArgumentException if data format is invalid
     */
    public static GitObject parse(byte[] rawData) {
        String header = new String(rawData, StandardCharsets.UTF_8);
        int nullIndex = header.indexOf('\0');
        
        if (nullIndex == -1) {
            throw new IllegalArgumentException("Invalid object format: missing null separator");
        }
        
        String headerPart = header.substring(0, nullIndex);
        String[] headerParts = headerPart.split(" ");
        
        if (headerParts.length != 2) {
            throw new IllegalArgumentException("Invalid object header format");
        }
        
        ObjectType type = ObjectType.fromString(headerParts[0]);
        
        try {
            int declaredSize = Integer.parseInt(headerParts[1]);
            int actualSize = rawData.length - nullIndex - 1;
            
            if (declaredSize != actualSize) {
                throw new IllegalArgumentException(String.format(
                    "Size mismatch: declared %d, actual %d", declaredSize, actualSize));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid size in header", e);
        }
        
        byte[] content = new byte[rawData.length - nullIndex - 1];
        System.arraycopy(rawData, nullIndex + 1, content, 0, content.length);
        
        return new GitObject(type, content);
    }
    
    /**
     * Serializes the GitObject to the Git object format.
     * Returns: "<type> <size>\0<content>"
     * 
     * @return serialized byte array
     */
    public byte[] serialize() {
        String header = type.getTypeString() + " " + content.length;
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        
        byte[] result = new byte[headerBytes.length + 1 + content.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        result[headerBytes.length] = '\0';
        System.arraycopy(content, 0, result, headerBytes.length + 1, content.length);
        
        return result;
    }
    
    /**
     * Gets the object type.
     * 
     * @return the object type
     */
    public ObjectType getType() {
        return type;
    }
    
    /**
     * Gets the object content.
     * 
     * @return copy of the content bytes
     */
    public byte[] getContent() {
        return content.clone(); // Defensive copy
    }
    
    /**
     * Gets the SHA-1 hash of the object.
     * 
     * @return 40-character hex string
     */
    public String getHash() {
        return hash;
    }
    
    /**
     * Gets the content as a string (UTF-8 encoded).
     * 
     * @return content as string
     */
    public String getContentAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }
    
    /**
     * Computes the SHA-1 hash of the serialized object.
     * 
     * @return the hash string
     */
    private String computeHash() {
        return Sha1Hasher.hash(serialize());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GitObject that = (GitObject) obj;
        return hash.equals(that.hash);
    }
    
    @Override
    public int hashCode() {
        return hash.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("GitObject{type=%s, hash=%s, size=%d}", 
                           type, hash, content.length);
    }
}

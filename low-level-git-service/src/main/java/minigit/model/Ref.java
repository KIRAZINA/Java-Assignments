package minigit.model;

import minigit.util.Sha1Hasher;

/**
 * Represents a Git reference (ref).
 * References can be either symbolic (pointing to another ref) or direct (pointing to a commit hash).
 */
public class Ref {
    
    private final String name;
    private final String target;
    private final boolean symbolic;
    
    /**
     * Creates a direct reference pointing to a commit hash.
     * 
     * @param name the ref name (e.g., "heads/main")
     * @param target the commit hash
     * @throws IllegalArgumentException if hash is invalid
     */
    public Ref(String name, String target) {
        this(name, target, false);
    }
    
    /**
     * Creates a reference.
     * 
     * @param name the ref name
     * @param target the target (hash or symbolic ref)
     * @param symbolic true if this is a symbolic ref, false if direct
     * @throws IllegalArgumentException if parameters are invalid
     */
    public Ref(String name, String target, boolean symbolic) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Ref name cannot be null or empty");
        }
        if (target == null || target.trim().isEmpty()) {
            throw new IllegalArgumentException("Ref target cannot be null or empty");
        }
        
        this.name = name.trim();
        this.target = target.trim();
        this.symbolic = symbolic;
        
        if (!symbolic && !Sha1Hasher.isValidHash(target)) {
            throw new IllegalArgumentException("Invalid hash for direct ref: " + target);
        }
    }
    
    /**
     * Parses a ref from its string representation.
     * 
     * @param name the ref name
     * @param content the ref content (either hash or "ref: <target>")
     * @return parsed Ref object
     */
    public static Ref parse(String name, String content) {
        if (content.startsWith("ref: ")) {
            return new Ref(name, content.substring(5), true);
        } else {
            return new Ref(name, content, false);
        }
    }
    
    /**
     * Gets the ref name.
     * 
     * @return the ref name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the target.
     * 
     * @return the target (hash or symbolic ref path)
     */
    public String getTarget() {
        return target;
    }
    
    /**
     * Checks if this is a symbolic reference.
     * 
     * @return true if symbolic, false if direct
     */
    public boolean isSymbolic() {
        return symbolic;
    }
    
    /**
     * Checks if this is a direct reference (points to a hash).
     * 
     * @return true if direct, false if symbolic
     */
    public boolean isDirect() {
        return !symbolic;
    }
    
    /**
     * Serializes the ref to its string representation.
     * 
     * @return the string representation
     */
    public String serialize() {
        if (symbolic) {
            return "ref: " + target;
        } else {
            return target;
        }
    }
    
    /**
     * Creates a symbolic reference.
     * 
     * @param name the ref name
     * @param target the symbolic target
     * @return symbolic Ref object
     */
    public static Ref symbolic(String name, String target) {
        return new Ref(name, target, true);
    }
    
    /**
     * Creates a direct reference.
     * 
     * @param name the ref name
     * @param target the commit hash
     * @return direct Ref object
     */
    public static Ref direct(String name, String target) {
        return new Ref(name, target, false);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Ref that = (Ref) obj;
        return symbolic == that.symbolic &&
               name.equals(that.name) &&
               target.equals(that.target);
    }
    
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + (symbolic ? 1 : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("Ref{name=%s, target=%s, symbolic=%s}", 
                           name, target, symbolic);
    }
}

package minigit.model;

/**
 * Enumeration of Git object types.
 * Represents the three main types of objects in Git: blob, tree, and commit.
 */
public enum ObjectType {
    BLOB("blob"),
    TREE("tree"),
    COMMIT("commit");
    
    private final String typeString;
    
    ObjectType(String typeString) {
        this.typeString = typeString;
    }
    
    /**
     * Gets the string representation of the object type as used in Git objects.
     * 
     * @return the type string (e.g., "blob", "tree", "commit")
     */
    public String getTypeString() {
        return typeString;
    }
    
    /**
     * Parses a string to get the corresponding ObjectType.
     * 
     * @param typeString the string to parse
     * @return the corresponding ObjectType
     * @throws IllegalArgumentException if the string doesn't match any type
     */
    public static ObjectType fromString(String typeString) {
        for (ObjectType type : values()) {
            if (type.typeString.equals(typeString)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown object type: " + typeString);
    }
}

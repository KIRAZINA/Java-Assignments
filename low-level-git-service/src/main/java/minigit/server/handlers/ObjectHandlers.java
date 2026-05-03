package minigit.server.handlers;

import minigit.core.Repository;
import minigit.server.Request;
import minigit.server.Response;
import minigit.util.Sha1Hasher;

/**
 * HTTP handlers for Git object operations.
 * Handles /objects endpoints for storing and retrieving Git objects.
 */
public class ObjectHandlers {
    
    private final Repository repository;
    
    /**
     * Creates ObjectHandlers with the given repository.
     * 
     * @param repository the Git repository
     */
    public ObjectHandlers(Repository repository) {
        this.repository = repository;
    }
    
    /**
     * Handles PUT /objects - stores a new Git object.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handlePutObject(Request request) {
        if (!request.hasBody()) {
            return Response.badRequest("Request body is required");
        }
        
        // Check content size limit (10MB as per spec)
        if (request.getContentLength() > 10 * 1024 * 1024) {
            return Response.badRequest("Object too large (max 10MB)");
        }
        
        try {
            // First, try to parse as Git object (for raw Git object bytes)
            // If that fails, create a new BLOB from the raw content
            minigit.model.GitObject gitObject;
            try {
                gitObject = minigit.model.GitObject.parse(request.getBody());
            } catch (IllegalArgumentException e) {
                // Not a valid Git object, create a blob
                gitObject = new minigit.model.GitObject(minigit.model.ObjectType.BLOB, request.getBody());
            }
            
            repository.getObjectStore().store(gitObject);
            String hash = gitObject.getHash();
            
            // Return 201 Created with Location header
            return Response.created("/objects/" + hash, "Object stored with hash: " + hash);
        } catch (Exception e) {
            return Response.internalServerError("Failed to store object: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /objects/{hash} - retrieves a Git object's content.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleGetObject(Request request) {
        String path = request.getPath();
        String hash = extractHashFromPath(path);
        
        if (hash == null || !Sha1Hasher.isValidHash(hash)) {
            return Response.notFound("Object not found: " + hash);
        }
        
        try {
            minigit.model.GitObject gitObject = repository.getObjectStore().retrieve(hash);
            if (gitObject == null) {
                return Response.notFound("Object not found: " + hash);
            }
            
            return Response.ok("application/octet-stream", gitObject.getContent());
        } catch (Exception e) {
            return Response.internalServerError("Failed to retrieve object: " + e.getMessage());
        }
    }
    
    /**
     * Handles HEAD /objects/{hash} - checks if an object exists.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleHeadObject(Request request) {
        String path = request.getPath();
        String hash = extractHashFromPath(path);
        
        if (hash == null || !Sha1Hasher.isValidHash(hash)) {
            return Response.notFound("Object not found: " + hash);
        }
        
        try {
            boolean exists = repository.getObjectStore().exists(hash);
            if (!exists) {
                return Response.notFound("Object not found: " + hash);
            }
            
            return new Response(Response.OK);
        } catch (Exception e) {
            return Response.internalServerError("Failed to check object: " + e.getMessage());
        }
    }
    
    /**
     * Extracts the hash from a path like "/objects/abc123...".
     * 
     * @param path the request path
     * @return the hash, or null if invalid
     */
    private String extractHashFromPath(String path) {
        if (path == null || !path.startsWith("/objects/")) {
            return null;
        }
        
        String hash = path.substring("/objects/".length());
        
        // Remove any query parameters
        int queryIndex = hash.indexOf('?');
        if (queryIndex != -1) {
            hash = hash.substring(0, queryIndex);
        }
        
        return hash.isEmpty() ? null : hash;
    }
}

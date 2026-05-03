package minigit.server.handlers;

import minigit.core.Repository;
import minigit.model.Ref;
import minigit.server.Request;
import minigit.server.Response;
import minigit.util.PathUtils;

import java.util.List;

/**
 * HTTP handlers for Git reference operations.
 * Handles /refs and /HEAD endpoints for managing Git references.
 */
public class RefHandlers {
    
    private final Repository repository;
    
    /**
     * Creates RefHandlers with the given repository.
     * 
     * @param repository the Git repository
     */
    public RefHandlers(Repository repository) {
        this.repository = repository;
    }
    
    /**
     * Handles GET /refs - lists all references.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleListRefs(Request request) {
        try {
            List<Ref> refs = repository.getRefManager().listRefs();
            
            StringBuilder response = new StringBuilder();
            for (Ref ref : refs) {
                response.append(ref.getName()).append(": ").append(ref.serialize()).append("\n");
            }
            
            return Response.ok("text/plain", response.toString());
        } catch (Exception e) {
            return Response.internalServerError("Failed to list refs: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /refs/{name} - reads a specific reference.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleGetRef(Request request) {
        String path = request.getPath();
        String refName = extractRefNameFromPath(path);
        
        if (refName == null || !PathUtils.isValidRefName(refName)) {
            return Response.badRequest("Invalid reference name");
        }
        
        try {
            Ref ref = repository.getRefManager().getRef(refName);
            if (ref == null) {
                return Response.notFound("Reference not found: " + refName);
            }
            
            return Response.ok("text/plain", ref.serialize());
        } catch (Exception e) {
            return Response.internalServerError("Failed to get ref: " + e.getMessage());
        }
    }
    
    /**
     * Handles PUT /refs/{name} - creates or updates a reference.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleUpdateRef(Request request) {
        String path = request.getPath();
        String refName = extractRefNameFromPath(path);
        
        if (refName == null || !PathUtils.isValidRefName(refName)) {
            return Response.badRequest("Invalid reference name");
        }
        
        if (!request.hasBody()) {
            return Response.badRequest("Request body is required");
        }
        
        String body = request.getBodyAsString().trim();
        if (body.isEmpty()) {
            return Response.badRequest("Request body cannot be empty");
        }
        
        try {
            boolean isSymbolic = body.startsWith("ref: ");
            
            // Fix #8 (partial): only HEAD may be a symbolic ref.
            // Regular refs (heads/*, tags/*) must point directly to a commit hash.
            if (isSymbolic) {
                return Response.badRequest(
                    "Symbolic references are not allowed in /refs/{name}. "
                    + "Use PUT /HEAD to create symbolic refs.");
            }
            
            // Check if the ref existed BEFORE storing
            boolean existed = repository.getRefManager().getRef(refName) != null;
            
            Ref ref = Ref.direct(refName, body);
            repository.getRefManager().storeRef(ref);
            
            int statusCode = existed ? Response.OK : Response.CREATED;
            return new Response(statusCode, "text/plain", "Reference updated: " + refName);
        } catch (IllegalArgumentException e) {
            return Response.badRequest("Invalid reference value: " + e.getMessage());
        } catch (Exception e) {
            return Response.internalServerError("Failed to update ref: " + e.getMessage());
        }
    }
    
    /**
     * Handles DELETE /refs/{name} - deletes a reference.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleDeleteRef(Request request) {
        String path = request.getPath();
        String refName = extractRefNameFromPath(path);
        
        if (refName == null || !PathUtils.isValidRefName(refName)) {
            return Response.badRequest("Invalid reference name");
        }
        
        try {
            boolean deleted = repository.getRefManager().deleteRef(refName);
            if (!deleted) {
                return Response.notFound("Reference not found: " + refName);
            }
            
            return Response.noContent();
        } catch (Exception e) {
            return Response.internalServerError("Failed to delete ref: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /HEAD - reads the HEAD reference.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleGetHead(Request request) {
        try {
            Ref headRef = repository.getRefManager().getHead();
            if (headRef == null) {
                return Response.notFound("HEAD not found");
            }
            
            return Response.ok("text/plain", headRef.serialize());
        } catch (Exception e) {
            return Response.internalServerError("Failed to get HEAD: " + e.getMessage());
        }
    }
    
    /**
     * Handles PUT /HEAD - updates the HEAD reference.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleUpdateHead(Request request) {
        if (!request.hasBody()) {
            return Response.badRequest("Request body is required");
        }
        
        String body = request.getBodyAsString().trim();
        if (body.isEmpty()) {
            return Response.badRequest("Request body cannot be empty");
        }
        
        try {
            // Check if this is a symbolic reference or direct reference
            boolean isSymbolic = body.startsWith("ref: ");
            String target = isSymbolic ? body.substring(5) : body;
            
            repository.getRefManager().setHead(target, isSymbolic);
            
            return Response.ok("text/plain", "HEAD updated");
        } catch (Exception e) {
            return Response.internalServerError("Failed to update HEAD: " + e.getMessage());
        }
    }
    
    /**
     * Extracts the reference name from a path like "/refs/heads/main" or "/refs/HEAD".
     * 
     * @param path the request path
     * @return the reference name, or null if invalid
     */
    private String extractRefNameFromPath(String path) {
        if (path == null || !path.startsWith("/refs/")) {
            return null;
        }
        
        String refName = path.substring("/refs/".length());
        
        // Remove any query parameters
        int queryIndex = refName.indexOf('?');
        if (queryIndex != -1) {
            refName = refName.substring(0, queryIndex);
        }
        
        return refName.isEmpty() ? null : refName;
    }
}

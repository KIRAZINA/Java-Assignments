package minigit.server.handlers;

import minigit.core.Repository;
import minigit.server.Request;
import minigit.server.Response;

/**
 * HTTP handlers for repository operations.
 * Handles /init and other repository-level endpoints.
 */
public class RepositoryHandlers {
    
    private final Repository repository;
    
    /**
     * Creates RepositoryHandlers with the given repository.
     * 
     * @param repository the Git repository
     */
    public RepositoryHandlers(Repository repository) {
        this.repository = repository;
    }
    
    /**
     * Handles POST /init - initializes a new Git repository.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleInit(Request request) {
        try {
            if (repository.isRepository()) {
                return Response.badRequest("Repository already initialized");
            }
            
            repository.initialize();
            
            return Response.created("Repository initialized successfully");
        } catch (Exception e) {
            return Response.internalServerError("Failed to initialize repository: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /status - returns repository status and statistics.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleStatus(Request request) {
        try {
            if (!repository.isRepository()) {
                return Response.notFound("Repository not initialized");
            }
            
            StringBuilder status = new StringBuilder();
            status.append("Repository Status:\n");
            status.append("Initialized: Yes\n");
            status.append("Statistics: ").append(repository.getStatistics()).append("\n");
            status.append("Valid: ").append(repository.validateRepository() ? "Yes" : "No").append("\n");
            
            // Add current HEAD information
            String currentCommit = repository.getRefManager().getCurrentCommit();
            if (currentCommit != null) {
                status.append("Current HEAD: ").append(currentCommit).append("\n");
            } else {
                status.append("Current HEAD: None\n");
            }
            
            // Add branch information
            var branches = repository.listBranches();
            status.append("Branches: ").append(branches.size()).append("\n");
            for (String branch : branches) {
                String branchHead = repository.getBranchHead(branch);
                status.append("  ").append(branch).append(": ").append(branchHead != null ? branchHead.substring(0, 7) : "None").append("\n");
            }
            
            return Response.ok("text/plain", status.toString());
        } catch (Exception e) {
            return Response.internalServerError("Failed to get status: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET / - returns basic server information.
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleRoot(Request request) {
        StringBuilder info = new StringBuilder();
        info.append("Mini-Git HTTP Server\n");
        info.append("=====================\n\n");
        info.append("Available endpoints:\n");
        info.append("  POST /init - Initialize repository\n");
        info.append("  GET /status - Repository status\n");
        info.append("  PUT /objects - Store object\n");
        info.append("  GET /objects/{hash} - Retrieve object\n");
        info.append("  HEAD /objects/{hash} - Check object existence\n");
        info.append("  GET /refs - List references\n");
        info.append("  GET /refs/{name} - Get reference\n");
        info.append("  PUT /refs/{name} - Update reference\n");
        info.append("  DELETE /refs/{name} - Delete reference\n");
        info.append("  GET /HEAD - Get HEAD\n");
        info.append("  PUT /HEAD - Update HEAD\n");
        
        return Response.ok("text/plain", info.toString());
    }
    
    /**
     * Handles POST /commit - creates a new commit (bonus endpoint).
     * 
     * @param request the HTTP request
     * @return HTTP response
     */
    public Response handleCommit(Request request) {
        if (!repository.isRepository()) {
            return Response.notFound("Repository not initialized");
        }
        
        if (!request.hasBody()) {
            return Response.badRequest("Request body is required");
        }
        
        String body = request.getBodyAsString().trim();
        if (body.isEmpty()) {
            return Response.badRequest("Request body cannot be empty");
        }
        
        try {
            // Parse commit data from body (simplified format: tree|parent|author|message)
            String[] parts = body.split("\n");
            if (parts.length < 3) {
                return Response.badRequest("Invalid commit format. Expected: tree\nparent\nauthor\nmessage");
            }
            
            String treeHash = parts[0].trim();
            String parentHash = parts[1].trim().isEmpty() ? null : parts[1].trim();
            String author = parts[2].trim();
            String message = parts.length > 3 ? parts[3].trim() : "";
            
            // Combine remaining lines as message
            if (parts.length > 4) {
                for (int i = 4; i < parts.length; i++) {
                    message += "\n" + parts[i];
                }
            }
            
            String commitHash = repository.createCommit(treeHash, parentHash, author, message);
            
            return Response.created("Commit created: " + commitHash);
        } catch (Exception e) {
            return Response.internalServerError("Failed to create commit: " + e.getMessage());
        }
    }
}

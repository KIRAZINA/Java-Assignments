package minigit.server;

/**
 * Functional interface for HTTP request handlers.
 * Handlers take a Request and return a Response.
 */
@FunctionalInterface
public interface Handler {
    
    /**
     * Handles an HTTP request.
     * 
     * @param request the HTTP request to handle
     * @return the HTTP response
     * @throws Exception if handling fails
     */
    Response handle(Request request) throws Exception;
}

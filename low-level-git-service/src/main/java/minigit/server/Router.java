package minigit.server;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple HTTP router that maps (method, path) patterns to handlers.
 * Uses string-based matching without regex libraries for simplicity.
 */
public class Router {
    
    private final List<Route> routes = new ArrayList<>();
    
    /**
     * Represents a route with method, path pattern, and handler.
     */
    private static class Route {
        final String method;
        final String pathPattern;
        final Handler handler;
        final boolean isParameterized;
        
        Route(String method, String pathPattern, Handler handler) {
            this.method = method.toUpperCase();
            this.pathPattern = pathPattern;
            this.handler = handler;
            this.isParameterized = pathPattern.contains("{") && pathPattern.contains("}");
        }
    }
    
    /**
     * Adds a route to the router.
     * 
     * @param method the HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param pathPattern the path pattern (can contain {param} placeholders)
     * @param handler the handler function
     */
    public void addRoute(String method, String pathPattern, Handler handler) {
        routes.add(new Route(method, pathPattern, handler));
    }
    
    /**
     * Adds a GET route.
     * 
     * @param pathPattern the path pattern
     * @param handler the handler function
     */
    public void get(String pathPattern, Handler handler) {
        addRoute("GET", pathPattern, handler);
    }
    
    /**
     * Adds a POST route.
     * 
     * @param pathPattern the path pattern
     * @param handler the handler function
     */
    public void post(String pathPattern, Handler handler) {
        addRoute("POST", pathPattern, handler);
    }
    
    /**
     * Adds a PUT route.
     * 
     * @param pathPattern the path pattern
     * @param handler the handler function
     */
    public void put(String pathPattern, Handler handler) {
        addRoute("PUT", pathPattern, handler);
    }
    
    /**
     * Adds a DELETE route.
     * 
     * @param pathPattern the path pattern
     * @param handler the handler function
     */
    public void delete(String pathPattern, Handler handler) {
        addRoute("DELETE", pathPattern, handler);
    }
    
    /**
     * Adds a HEAD route.
     * 
     * @param pathPattern the path pattern
     * @param handler the handler function
     */
    public void head(String pathPattern, Handler handler) {
        addRoute("HEAD", pathPattern, handler);
    }
    
    /**
     * Routes a request to the appropriate handler.
     * 
     * @param request the HTTP request
     * @return the handler response, or null if no route matches
     */
    public Response route(Request request) {
        String method = request.getMethod().toUpperCase();
        String path = request.getPath();
        
        // Remove query string from path
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }
        
        for (Route route : routes) {
            if (!route.method.equals(method) && !route.method.equals("*")) {
                continue;
            }
            
            if (matchesPath(route.pathPattern, path)) {
                try {
                    return route.handler.handle(request);
                } catch (Exception e) {
                    return Response.internalServerError("Internal server error: " + e.getMessage());
                }
            }
        }
        
        return null; // No route matched
    }
    
    /**
     * Checks if a path pattern matches the actual path.
     * Supports simple parameter matching with {param} placeholders.
     * 
     * @param pattern the path pattern
     * @param path the actual path
     * @return true if matches, false otherwise
     */
    private boolean matchesPath(String pattern, String path) {
        if (!pattern.contains("{")) {
            // Simple exact match
            return pattern.equals(path) || 
                   (pattern.endsWith("/") && pattern.substring(0, pattern.length() - 1).equals(path)) ||
                   (path.endsWith("/") && path.substring(0, path.length() - 1).equals(pattern));
        }
        
        // Parameterized matching
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        
        if (patternParts.length != pathParts.length) {
            return false;
        }
        
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];
            
            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                // Parameter placeholder - matches anything
                continue;
            } else if (!patternPart.equals(pathPart)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Extracts path parameters from a matching path.
     * 
     * @param pattern the path pattern with {param} placeholders
     * @param path the actual path
     * @return array of parameter values, or empty array if no parameters
     */
    public String[] extractParameters(String pattern, String path) {
        if (!pattern.contains("{")) {
            return new String[0];
        }
        
        List<String> parameters = new ArrayList<>();
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                parameters.add(pathParts[i]);
            }
        }
        
        return parameters.toArray(new String[0]);
    }
    
    /**
     * Gets all registered routes.
     * 
     * @return list of route descriptions
     */
    public List<String> getRoutes() {
        List<String> routeDescriptions = new ArrayList<>();
        for (Route route : routes) {
            routeDescriptions.add(route.method + " " + route.pathPattern);
        }
        return routeDescriptions;
    }
    
    /**
     * Clears all routes.
     */
    public void clear() {
        routes.clear();
    }
    
    /**
     * Gets the number of registered routes.
     * 
     * @return the route count
     */
    public int getRouteCount() {
        return routes.size();
    }
}

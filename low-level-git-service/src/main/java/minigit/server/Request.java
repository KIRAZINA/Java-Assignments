package minigit.server;

import java.util.Map;

/**
 * Represents an HTTP request.
 * Contains method, path, version, headers, and body.
 */
public class Request {
    
    private final String method;
    private final String path;
    private final String version;
    private final Map<String, String> headers;
    private final byte[] body;
    
    /**
     * Creates a new Request.
     * 
     * @param method the HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param path the request path
     * @param version the HTTP version (e.g., "HTTP/1.1")
     * @param headers the request headers (case-insensitive keys)
     * @param body the request body (may be null)
     */
    public Request(String method, String path, String version, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.version = version;
        this.headers = headers;
        this.body = body;
    }
    
    /**
     * Gets the HTTP method.
     * 
     * @return the method
     */
    public String getMethod() {
        return method;
    }
    
    /**
     * Gets the request path.
     * 
     * @return the path
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Gets the HTTP version.
     * 
     * @return the version
     */
    public String getVersion() {
        return version;
    }
    
    /**
     * Gets the request headers.
     * 
     * @return immutable map of headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    /**
     * Gets a specific header value.
     * 
     * @param name the header name (case-insensitive)
     * @return the header value, or null if not present
     */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }
    
    /**
     * Gets the request body.
     * 
     * @return the body bytes, or null if no body
     */
    public byte[] getBody() {
        return body;
    }
    
    /**
     * Gets the request body as a string (UTF-8 encoded).
     * 
     * @return the body as string, or null if no body
     */
    public String getBodyAsString() {
        if (body == null) {
            return null;
        }
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * Checks if the request has a body.
     * 
     * @return true if the request has a body, false otherwise
     */
    public boolean hasBody() {
        return body != null && body.length > 0;
    }
    
    /**
     * Gets the Content-Length header value.
     * 
     * @return the content length, or 0 if not specified
     */
    public int getContentLength() {
        String contentLength = getHeader("content-length");
        if (contentLength == null) {
            return 0;
        }
        try {
            return Integer.parseInt(contentLength);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Checks if the connection should be kept alive.
     * 
     * @return true if keep-alive, false if close
     */
    public boolean isKeepAlive() {
        String connection = getHeader("connection");
        if (connection == null) {
            // HTTP/1.1 defaults to keep-alive, HTTP/1.0 defaults to close
            return version.equals("HTTP/1.1");
        }
        return !connection.equalsIgnoreCase("close");
    }
    
    @Override
    public String toString() {
        return String.format("Request{method=%s, path=%s, version=%s, headers=%d, body=%d}", 
                           method, path, version, headers.size(), 
                           body != null ? body.length : 0);
    }
}

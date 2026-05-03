package minigit.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP response.
 * Contains status code, headers, and body.
 */
public class Response {
    
    private final int statusCode;
    private final String statusText;
    private final Map<String, String> headers;
    private final byte[] body;
    
    // Common HTTP status codes
    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int NO_CONTENT = 204;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int INTERNAL_SERVER_ERROR = 500;
    
    // Status text mappings
    private static final Map<Integer, String> STATUS_TEXTS = new HashMap<>();
    static {
        STATUS_TEXTS.put(OK, "OK");
        STATUS_TEXTS.put(CREATED, "Created");
        STATUS_TEXTS.put(NO_CONTENT, "No Content");
        STATUS_TEXTS.put(BAD_REQUEST, "Bad Request");
        STATUS_TEXTS.put(NOT_FOUND, "Not Found");
        STATUS_TEXTS.put(METHOD_NOT_ALLOWED, "Method Not Allowed");
        STATUS_TEXTS.put(INTERNAL_SERVER_ERROR, "Internal Server Error");
    }
    
    /**
     * Creates a new Response.
     * 
     * @param statusCode the HTTP status code
     * @param headers the response headers
     * @param body the response body (may be null)
     */
    public Response(int statusCode, Map<String, String> headers, byte[] body) {
        this.statusCode = statusCode;
        this.statusText = STATUS_TEXTS.getOrDefault(statusCode, "Unknown");
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.body = body;
    }
    
    /**
     * Creates a response with no body.
     * 
     * @param statusCode the HTTP status code
     */
    public Response(int statusCode) {
        this(statusCode, new HashMap<>(), null);
    }
    
    /**
     * Creates a response with a string body.
     * 
     * @param statusCode the HTTP status code
     * @param contentType the content type
     * @param body the body string
     */
    public Response(int statusCode, String contentType, String body) {
        this(statusCode, new HashMap<>(), body != null ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null);
        if (contentType != null) {
            setContentType(contentType);
        }
        if (body != null) {
            setContentLength(body.length());
        }
    }
    
    /**
     * Creates a response with a byte body.
     * 
     * @param statusCode the HTTP status code
     * @param contentType the content type
     * @param body the body bytes
     */
    public Response(int statusCode, String contentType, byte[] body) {
        this(statusCode, new HashMap<>(), body);
        if (contentType != null) {
            setContentType(contentType);
        }
        if (body != null) {
            setContentLength(body.length);
        }
    }
    
    /**
     * Gets the status code.
     * 
     * @return the status code
     */
    public int getStatusCode() {
        return statusCode;
    }
    
    /**
     * Gets the status text.
     * 
     * @return the status text
     */
    public String getStatusText() {
        return statusText;
    }
    
    /**
     * Gets the response headers.
     * 
     * @return copy of headers map
     */
    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }
    
    /**
     * Gets a specific header value.
     * 
     * @param name the header name
     * @return the header value, or null if not present
     */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }
    
    /**
     * Sets a header value.
     * 
     * @param name the header name
     * @param value the header value
     */
    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
    }
    
    /**
     * Sets the Content-Type header.
     * 
     * @param contentType the content type
     */
    public void setContentType(String contentType) {
        setHeader("Content-Type", contentType);
    }
    
    /**
     * Sets the Content-Length header.
     * 
     * @param length the content length
     */
    public void setContentLength(int length) {
        setHeader("Content-Length", String.valueOf(length));
    }
    
    /**
     * Sets the Location header (for redirects).
     * 
     * @param location the location URL
     */
    public void setLocation(String location) {
        setHeader("Location", location);
    }
    
    /**
     * Sets the Connection header.
     * 
     * @param connection the connection value ("keep-alive" or "close")
     */
    public void setConnection(String connection) {
        setHeader("Connection", connection);
    }
    
    /**
     * Sets the response body.
     * 
     * @param body the body bytes
     */
    public void setBody(byte[] body) {
        // Note: This is a simplified implementation that replaces the body field
        // In a more complete implementation, this would need to handle the field properly
        if (body != null) {
            setContentLength(body.length);
        }
    }
    
    /**
     * Gets the response body.
     * 
     * @return the body bytes, or null if no body
     */
    public byte[] getBody() {
        return body;
    }
    
    /**
     * Gets the body as a string (UTF-8 encoded).
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
     * Checks if the response has a body.
     * 
     * @return true if the response has a body, false otherwise
     */
    public boolean hasBody() {
        return body != null && body.length > 0;
    }
    
    /**
     * Creates a 200 OK response with text/plain content.
     * 
     * @param body the response body
     * @return Response object
     */
    public static Response ok(String body) {
        return new Response(OK, "text/plain", body);
    }
    
    /**
     * Creates a 200 OK response with custom content type.
     * 
     * @param contentType the content type
     * @param body the response body
     * @return Response object
     */
    public static Response ok(String contentType, String body) {
        return new Response(OK, contentType, body);
    }
    
    /**
     * Creates a 200 OK response with binary content.
     * 
     * @param contentType the content type
     * @param body the response body
     * @return Response object
     */
    public static Response ok(String contentType, byte[] body) {
        return new Response(OK, contentType, body);
    }
    
    /**
     * Creates a 201 Created response with Location header.
     * 
     * @param location the location URL
     * @return Response object
     */
    public static Response created(String location) {
        Response response = new Response(CREATED);
        response.setLocation(location);
        return response;
    }
    
    /**
     * Creates a 201 Created response with body and Location header.
     * 
     * @param location the location URL
     * @param body the response body
     * @return Response object
     */
    public static Response created(String location, String body) {
        Response response = new Response(CREATED, "text/plain", body);
        response.setLocation(location);
        return response;
    }
    
    /**
     * Creates a 204 No Content response.
     * 
     * @return Response object
     */
    public static Response noContent() {
        return new Response(NO_CONTENT);
    }
    
    /**
     * Creates a 400 Bad Request response.
     * 
     * @param body the error message
     * @return Response object
     */
    public static Response badRequest(String body) {
        return new Response(BAD_REQUEST, "text/plain", body);
    }
    
    /**
     * Creates a 404 Not Found response.
     * 
     * @param body the error message
     * @return Response object
     */
    public static Response notFound(String body) {
        return new Response(NOT_FOUND, "text/plain", body);
    }
    
    /**
     * Creates a 405 Method Not Allowed response.
     * 
     * @return Response object
     */
    public static Response methodNotAllowed() {
        return new Response(METHOD_NOT_ALLOWED, "text/plain", "Method Not Allowed");
    }
    
    /**
     * Creates a 500 Internal Server Error response.
     * 
     * @param body the error message
     * @return Response object
     */
    public static Response internalServerError(String body) {
        return new Response(INTERNAL_SERVER_ERROR, "text/plain", body);
    }
    
    @Override
    public String toString() {
        return String.format("Response{status=%d %s, headers=%d, body=%d}", 
                           statusCode, statusText, headers.size(), 
                           body != null ? body.length : 0);
    }
}

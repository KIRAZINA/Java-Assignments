package minigit.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes HTTP responses to OutputStream.
 * Handles proper HTTP response formatting.
 */
public class ResponseWriter {
    
    /**
     * Writes an HTTP response to the output stream.
     * 
     * @param response the response to write
     * @param outputStream the output stream to write to
     * @throws IOException if writing fails
     */
    public static void write(Response response, OutputStream outputStream) throws IOException {
        // Write status line
        String statusLine = String.format("HTTP/1.1 %d %s\r\n",
                                         response.getStatusCode(),
                                         response.getStatusText());
        outputStream.write(statusLine.getBytes(StandardCharsets.UTF_8));
        
        // Write all headers that the Response object already carries
        for (String headerName : response.getHeaders().keySet()) {
            String headerValue = response.getHeader(headerName);
            String headerLine = String.format("%s: %s\r\n", headerName, headerValue);
            outputStream.write(headerLine.getBytes(StandardCharsets.UTF_8));
        }
        
        // Fix #14: always include Content-Length so HTTP/1.1 keep-alive clients know
        // when the body ends. Response constructors set it for string/byte bodies;
        // we only add it here when it was omitted (e.g. status-only responses).
        if (response.getHeader("content-length") == null) {
            int bodyLength = response.hasBody() ? response.getBody().length : 0;
            String clHeader = String.format("Content-Length: %d\r\n", bodyLength);
            outputStream.write(clHeader.getBytes(StandardCharsets.UTF_8));
        }
        
        // Add Date header if not present
        if (response.getHeader("date") == null) {
            String dateHeader = String.format("Date: %s\r\n", getCurrentDate());
            outputStream.write(dateHeader.getBytes(StandardCharsets.UTF_8));
        }
        
        // Add Server header
        outputStream.write("Server: Mini-Git/1.0\r\n".getBytes(StandardCharsets.UTF_8));
        
        // Blank line — end of headers
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        
        // Write body if present
        if (response.hasBody()) {
            outputStream.write(response.getBody());
        }
        
        outputStream.flush();
    }
    
    /**
     * Gets the current date in RFC 1123 format.
     * 
     * @return formatted date string
     */
    private static String getCurrentDate() {
        return java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(java.time.ZoneOffset.UTC)
                .format(java.time.Instant.now());
    }
    
    /**
     * Writes a simple error response.
     * 
     * @param statusCode the status code
     * @param message the error message
     * @param outputStream the output stream
     * @throws IOException if writing fails
     */
    public static void writeError(int statusCode, String message, OutputStream outputStream) throws IOException {
        Response response = new Response(statusCode, "text/plain", message);
        write(response, outputStream);
    }
    
    /**
     * Writes a 400 Bad Request response.
     * 
     * @param message the error message
     * @param outputStream the output stream
     * @throws IOException if writing fails
     */
    public static void writeBadRequest(String message, OutputStream outputStream) throws IOException {
        writeError(Response.BAD_REQUEST, message, outputStream);
    }
    
    /**
     * Writes a 404 Not Found response.
     * 
     * @param message the error message
     * @param outputStream the output stream
     * @throws IOException if writing fails
     */
    public static void writeNotFound(String message, OutputStream outputStream) throws IOException {
        writeError(Response.NOT_FOUND, message, outputStream);
    }
    
    /**
     * Writes a 405 Method Not Allowed response.
     * 
     * @param outputStream the output stream
     * @throws IOException if writing fails
     */
    public static void writeMethodNotAllowed(OutputStream outputStream) throws IOException {
        writeError(Response.METHOD_NOT_ALLOWED, "Method Not Allowed", outputStream);
    }
    
    /**
     * Writes a 500 Internal Server Error response.
     * 
     * @param message the error message
     * @param outputStream the output stream
     * @throws IOException if writing fails
     */
    public static void writeInternalServerError(String message, OutputStream outputStream) throws IOException {
        writeError(Response.INTERNAL_SERVER_ERROR, message, outputStream);
    }
}

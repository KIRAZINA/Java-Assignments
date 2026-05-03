package minigit.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses raw HTTP requests into Request objects.
 * Handles request line, headers, and body parsing.
 */
public class RequestParser {
    
    /**
     * Parses an HTTP request from an InputStream.
     * 
     * @param inputStream the input stream to read from
     * @return parsed Request object
     * @throws IOException if reading fails or request is malformed
     */
    public static Request parse(InputStream inputStream) throws IOException {
        // Read the request line
        String requestLine = readLine(inputStream);
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request line");
        }
        
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length != 3) {
            throw new IOException("Invalid request line format: " + requestLine);
        }
        
        String method = requestParts[0];
        String path = requestParts[1];
        String version = requestParts[2];
        
        // Validate HTTP version
        if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) {
            throw new IOException("Unsupported HTTP version: " + version);
        }
        
        // Parse headers
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = readLine(inputStream)) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex <= 0) {
                throw new IOException("Invalid header format: " + headerLine);
            }
            
            String name = headerLine.substring(0, colonIndex).trim();
            String value = headerLine.substring(colonIndex + 1).trim();
            headers.put(name.toLowerCase(), value); // Normalize header names to lowercase
        }
        
        // Parse body if present
        byte[] body = null;
        String contentLengthStr = headers.get("content-length");
        String transferEncoding = headers.get("transfer-encoding");
        
        if (contentLengthStr != null) {
            try {
                int contentLength = Integer.parseInt(contentLengthStr);
                if (contentLength > 0) {
                    body = readBytes(inputStream, contentLength);
                }
            } catch (NumberFormatException e) {
                throw new IOException("Invalid Content-Length: " + contentLengthStr);
            }
        } else if (transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked")) {
            body = readChunkedBody(inputStream);
        } else if (headers.containsKey("connection") && headers.get("connection").equalsIgnoreCase("close")) {
            // Read until connection closes (simplified approach)
            body = readAllAvailable(inputStream);
        }
        
        return new Request(method, path, version, headers, body);
    }
    
    /**
     * Reads a line from the input stream.
     * Handles both CRLF and LF line endings.
     * 
     * @param inputStream the input stream
     * @return the line without line ending, or null if end of stream
     * @throws IOException if reading fails
     */
    private static String readLine(InputStream inputStream) throws IOException {
        StringBuilder line = new StringBuilder();
        int b;
        
        while ((b = inputStream.read()) != -1) {
            if (b == '\r') {
                // Check for LF
                int next = inputStream.read();
                if (next == '\n') {
                    break; // CRLF line ending
                } else if (next != -1) {
                    line.append((char) b);
                    line.append((char) next);
                    continue;
                }
            } else if (b == '\n') {
                break; // LF line ending
            }
            
            line.append((char) b);
        }
        
        if (b == -1 && line.length() == 0) {
            return null;
        }
        
        return line.toString();
    }
    
    /**
     * Reads exactly the specified number of bytes.
     * 
     * @param inputStream the input stream
     * @param count number of bytes to read
     * @return byte array containing the read bytes
     * @throws IOException if reading fails or end of stream reached prematurely
     */
    private static byte[] readBytes(InputStream inputStream, int count) throws IOException {
        byte[] buffer = new byte[count];
        int totalRead = 0;
        
        while (totalRead < count) {
            int read = inputStream.read(buffer, totalRead, count - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream");
            }
            totalRead += read;
        }
        
        return buffer;
    }
    
    /**
     * Reads chunked transfer encoding body.
     * Simplified implementation - doesn't handle chunk extensions.
     * 
     * @param inputStream the input stream
     * @return the decoded body bytes
     * @throws IOException if reading fails
     */
    private static byte[] readChunkedBody(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream bodyStream = new java.io.ByteArrayOutputStream();
        
        while (true) {
            // Read chunk size line
            String chunkSizeLine = readLine(inputStream);
            if (chunkSizeLine == null) {
                throw new IOException("Unexpected end of stream in chunked body");
            }
            
            // Parse chunk size (hexadecimal)
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeLine.trim(), 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid chunk size: " + chunkSizeLine);
            }
            
            // Zero chunk size indicates end of message
            if (chunkSize == 0) {
                // Read trailing headers (ignored in this implementation)
                String line;
                while ((line = readLine(inputStream)) != null && !line.isEmpty()) {
                    // Skip trailing headers
                }
                break;
            }
            
            // Read chunk data
            byte[] chunkData = readBytes(inputStream, chunkSize);
            bodyStream.write(chunkData);
            
            // Read CRLF after chunk data
            String crlf = readLine(inputStream);
            if (crlf != null && !crlf.isEmpty()) {
                throw new IOException("Expected CRLF after chunk data");
            }
        }
        
        return bodyStream.toByteArray();
    }
    
    /**
     * Reads all available bytes from the input stream.
     * This is a fallback method when no content length is specified.
     * 
     * @param inputStream the input stream
     * @return all available bytes
     * @throws IOException if reading fails
     */
    private static byte[] readAllAvailable(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] tempBuffer = new byte[4096];
        int read;
        
        while ((read = inputStream.read(tempBuffer)) != -1) {
            buffer.write(tempBuffer, 0, read);
            
            // Stop reading if we detect end of HTTP message
            // This is a simplified approach - in practice, you'd need better detection
            if (read < tempBuffer.length) {
                break;
            }
        }
        
        return buffer.toByteArray();
    }
}

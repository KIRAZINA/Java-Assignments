package minigit.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import minigit.util.Sha1Hasher;

/**
 * Integration tests for the HTTP server.
 * Tests full HTTP round-trips with a real server.
 */
public class IntegrationTest {
    
    @TempDir
    Path tempDir;
    
    private HttpServer server;
    private Thread serverThread;
    private static final int PORT = 18080;
    private static final String BASE_URL = "http://localhost:" + PORT;
    
    @BeforeEach
    public void setUp() throws IOException {
        server = new HttpServer(PORT, tempDir.toString());
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // Expected when server is stopped
            }
        });
        serverThread.start();
        
        // Wait for server to start
        waitForServer();
    }
    
    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
    
    private void waitForServer() {
        int attempts = 0;
        while (attempts < 10) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/").openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(100);
                conn.getResponseCode();
                return;
            } catch (IOException e) {
                attempts++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        throw new RuntimeException("Server failed to start");
    }
    
    @Test
    public void testRootEndpoint() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/").openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        assertEquals("text/plain", conn.getContentType());
        
        String response = readResponse(conn);
        assertTrue(response.contains("Mini-Git HTTP Server"));
        assertTrue(response.contains("Available endpoints"));
    }
    
    @Test
    public void testInitRepository() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        conn.setRequestMethod("POST");
        
        assertEquals(201, conn.getResponseCode());
        
        String response = readResponse(conn);
        assertTrue(response.contains("Repository initialized"));
        
        // Verify repository exists
        assertTrue(tempDir.resolve(".mini-git").toFile().exists());
    }
    
    @Test
    public void testInitRepositoryTwice() throws IOException {
        // Initialize once
        HttpURLConnection conn1 = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        conn1.setRequestMethod("POST");
        assertEquals(201, conn1.getResponseCode());
        
        // Try to initialize again
        HttpURLConnection conn2 = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        conn2.setRequestMethod("POST");
        assertEquals(400, conn2.getResponseCode());
        
        String response = readErrorResponse(conn2);
        assertTrue(response.contains("Repository already initialized"));
    }
    
    @Test
    public void testStoreAndRetrieveObject() throws IOException {
        // Initialize repository
        HttpURLConnection initConn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        initConn.setRequestMethod("POST");
        assertEquals(201, initConn.getResponseCode());
        
        // Store an object
        String content = "Hello, World!";
        HttpURLConnection putConn = (HttpURLConnection) new URL(BASE_URL + "/objects").openConnection();
        putConn.setRequestMethod("PUT");
        putConn.setDoOutput(true);
        putConn.setRequestProperty("Content-Type", "application/octet-stream");
        
        try (OutputStream os = putConn.getOutputStream()) {
            os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        
        assertEquals(201, putConn.getResponseCode());
        String location = putConn.getHeaderField("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/objects/"));
        
        String hash = location.substring("/objects/".length());
        assertTrue(Sha1Hasher.isValidHash(hash));
        
        // Retrieve the object
        HttpURLConnection getConn = (HttpURLConnection) new URL(BASE_URL + "/objects/" + hash).openConnection();
        getConn.setRequestMethod("GET");
        
        assertEquals(200, getConn.getResponseCode());
        assertEquals("application/octet-stream", getConn.getContentType());
        
        String retrievedContent = readResponse(getConn);
        assertEquals(content, retrievedContent);
    }
    
    @Test
    public void testRetrieveNonExistentObject() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/objects/nonexistent").openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(404, conn.getResponseCode());
        
        String response = readErrorResponse(conn);
        assertTrue(response.contains("Object not found"));
    }
    
    @Test
    public void testHeadObject() throws IOException {
        // Initialize repository and store an object
        HttpURLConnection initConn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        initConn.setRequestMethod("POST");
        assertEquals(201, initConn.getResponseCode());
        
        String content = "test content";
        HttpURLConnection putConn = (HttpURLConnection) new URL(BASE_URL + "/objects").openConnection();
        putConn.setRequestMethod("PUT");
        putConn.setDoOutput(true);
        
        try (OutputStream os = putConn.getOutputStream()) {
            os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(201, putConn.getResponseCode());
        
        String hash = putConn.getHeaderField("Location").substring("/objects/".length());
        
        // HEAD request
        HttpURLConnection headConn = (HttpURLConnection) new URL(BASE_URL + "/objects/" + hash).openConnection();
        headConn.setRequestMethod("HEAD");
        
        assertEquals(200, headConn.getResponseCode());
        
        // HEAD request for non-existent object
        HttpURLConnection headConn2 = (HttpURLConnection) new URL(BASE_URL + "/objects/nonexistent").openConnection();
        headConn2.setRequestMethod("HEAD");
        
        assertEquals(404, headConn2.getResponseCode());
    }
    
    @Test
    public void testCreateAndUpdateRef() throws IOException {
        // Initialize repository
        HttpURLConnection initConn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        initConn.setRequestMethod("POST");
        assertEquals(201, initConn.getResponseCode());
        
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        
        // Create a ref
        HttpURLConnection putConn = (HttpURLConnection) new URL(BASE_URL + "/refs/heads/main").openConnection();
        putConn.setRequestMethod("PUT");
        putConn.setDoOutput(true);
        putConn.setRequestProperty("Content-Type", "text/plain");
        
        try (OutputStream os = putConn.getOutputStream()) {
            os.write(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        
        int responseCode = putConn.getResponseCode();
        assertTrue(responseCode == 200 || responseCode == 201, 
                     "Expected 200 or 201, got: " + responseCode);
        
        // Get the ref
        HttpURLConnection getConn = (HttpURLConnection) new URL(BASE_URL + "/refs/heads/main").openConnection();
        getConn.setRequestMethod("GET");
        
        assertEquals(200, getConn.getResponseCode());
        String response = readResponse(getConn);
        assertEquals(hash, response.trim());
        
        // Update the ref
        String newHash = "3bbf7c46c94fcfb415dbe95f408b9ce91ee846ef";
        HttpURLConnection updateConn = (HttpURLConnection) new URL(BASE_URL + "/refs/heads/main").openConnection();
        updateConn.setRequestMethod("PUT");
        updateConn.setDoOutput(true);
        
        try (OutputStream os = updateConn.getOutputStream()) {
            os.write(newHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        
        assertEquals(200, updateConn.getResponseCode());
        
        // Verify update
        HttpURLConnection getConn2 = (HttpURLConnection) new URL(BASE_URL + "/refs/heads/main").openConnection();
        getConn2.setRequestMethod("GET");
        
        assertEquals(200, getConn2.getResponseCode());
        String response2 = readResponse(getConn2);
        assertEquals(newHash, response2.trim());
    }
    
    @Test
    public void testListRefs() throws IOException {
        // Initialize repository
        HttpURLConnection initConn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        initConn.setRequestMethod("POST");
        assertEquals(201, initConn.getResponseCode());
        
        // Create some refs
        String hash1 = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        String hash2 = "3bbf7c46c94fcfb415dbe95f408b9ce91ee846ef";
        
        HttpURLConnection putConn1 = (HttpURLConnection) new URL(BASE_URL + "/refs/heads/main").openConnection();
        putConn1.setRequestMethod("PUT");
        putConn1.setDoOutput(true);
        try (OutputStream os = putConn1.getOutputStream()) {
            os.write(hash1.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int rc1 = putConn1.getResponseCode();
        assertTrue(rc1 == 200 || rc1 == 201);
        
        HttpURLConnection putConn2 = (HttpURLConnection) new URL(BASE_URL + "/refs/heads/feature").openConnection();
        putConn2.setRequestMethod("PUT");
        putConn2.setDoOutput(true);
        try (OutputStream os = putConn2.getOutputStream()) {
            os.write(hash2.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int rc2 = putConn2.getResponseCode();
        assertTrue(rc2 == 200 || rc2 == 201);
        
        // List refs
        HttpURLConnection listConn = (HttpURLConnection) new URL(BASE_URL + "/refs").openConnection();
        listConn.setRequestMethod("GET");
        
        assertEquals(200, listConn.getResponseCode());
        String response = readResponse(listConn);
        
        assertTrue(response.contains("HEAD:"));
        assertTrue(response.contains("heads/main:"));
        assertTrue(response.contains("heads/feature:"));
        assertTrue(response.contains(hash1));
        assertTrue(response.contains(hash2));
    }
    
    @Test
    public void testHeadOperations() throws IOException {
        // Initialize repository
        HttpURLConnection initConn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        initConn.setRequestMethod("POST");
        assertEquals(201, initConn.getResponseCode());
        
        // Get HEAD (should be symbolic to main)
        HttpURLConnection getConn = (HttpURLConnection) new URL(BASE_URL + "/HEAD").openConnection();
        getConn.setRequestMethod("GET");
        
        assertEquals(200, getConn.getResponseCode());
        String response = readResponse(getConn);
        assertEquals("ref: refs/heads/main", response.trim());
        
        // Update HEAD to direct hash
        String hash = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed";
        HttpURLConnection putConn = (HttpURLConnection) new URL(BASE_URL + "/HEAD").openConnection();
        putConn.setRequestMethod("PUT");
        putConn.setDoOutput(true);
        
        try (OutputStream os = putConn.getOutputStream()) {
            os.write(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        
        assertEquals(200, putConn.getResponseCode());
        
        // Verify HEAD update
        HttpURLConnection getConn2 = (HttpURLConnection) new URL(BASE_URL + "/HEAD").openConnection();
        getConn2.setRequestMethod("GET");
        
        assertEquals(200, getConn2.getResponseCode());
        String response2 = readResponse(getConn2);
        assertEquals(hash, response2.trim());
    }
    
    @Test
    public void testStatus() throws IOException {
        // Initialize repository
        HttpURLConnection initConn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        initConn.setRequestMethod("POST");
        assertEquals(201, initConn.getResponseCode());
        
        // Get status
        HttpURLConnection statusConn = (HttpURLConnection) new URL(BASE_URL + "/status").openConnection();
        statusConn.setRequestMethod("GET");
        
        assertEquals(200, statusConn.getResponseCode());
        String response = readResponse(statusConn);
        
        assertTrue(response.contains("Repository Status:"));
        assertTrue(response.contains("Initialized: Yes"));
        assertTrue(response.contains("Valid: Yes"));
        assertTrue(response.contains("Current HEAD: None"));
    }
    
    @Test
    public void testInvalidEndpoint() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/invalid").openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(404, conn.getResponseCode());
        
        String response = readErrorResponse(conn);
        assertTrue(response.contains("Not Found"));
    }
    
    @Test
    public void testInvalidMethod() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/init").openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(405, conn.getResponseCode());
    }
    
    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    response.append("\n");
                }
                response.append(line);
                first = false;
            }
            return response.toString();
        }
    }
    
    private String readErrorResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    response.append("\n");
                }
                response.append(line);
                first = false;
            }
            return response.toString();
        }
    }
}

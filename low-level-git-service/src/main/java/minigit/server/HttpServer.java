package minigit.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;

import minigit.core.Repository;
import minigit.server.handlers.ObjectHandlers;
import minigit.server.handlers.RefHandlers;
import minigit.server.handlers.RepositoryHandlers;

/**
 * Main HTTP server implementation for Mini-Git.
 * Uses raw ServerSocket to handle HTTP requests without any frameworks.
 */
public class HttpServer {
    
    private int port;
    private final Repository repository;
    private final Router router;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    
    /**
     * Creates an HttpServer on the specified port.
     * 
     * @param port the port to listen on (0 for random available port)
     * @param repositoryPath the path to the repository directory
     */
    public HttpServer(int port, String repositoryPath) {
        this.port = port;
        this.repository = new Repository(Paths.get(repositoryPath));
        this.router = new Router();
        setupRoutes();
    }
    
    /**
     * Sets up all HTTP routes.
     */
    private void setupRoutes() {
        RepositoryHandlers repoHandlers = new RepositoryHandlers(repository);
        ObjectHandlers objectHandlers = new ObjectHandlers(repository);
        RefHandlers refHandlers = new RefHandlers(repository);
        
        // Repository endpoints
        router.get("/", repoHandlers::handleRoot);
        router.post("/init", repoHandlers::handleInit);
        router.get("/status", repoHandlers::handleStatus);
        router.post("/commit", repoHandlers::handleCommit);
        
        // Object endpoints
        router.put("/objects", objectHandlers::handlePutObject);
        router.get("/objects/{hash}", objectHandlers::handleGetObject);
        router.head("/objects/{hash}", objectHandlers::handleHeadObject);
        
        // Reference endpoints
        router.get("/refs", refHandlers::handleListRefs);
        router.get("/refs/{name}", refHandlers::handleGetRef);
        router.put("/refs/{name}", refHandlers::handleUpdateRef);
        router.delete("/refs/{name}", refHandlers::handleDeleteRef);
        
        // HEAD endpoints
        router.get("/HEAD", refHandlers::handleGetHead);
        router.put("/HEAD", refHandlers::handleUpdateHead);
    }
    
    /**
     * Starts the HTTP server.
     * 
     * @throws IOException if server cannot be started
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }
        
        serverSocket = new ServerSocket(port);
        if (port == 0) {
            this.port = serverSocket.getLocalPort();
        }
        running = true;
        System.out.println("Mini-Git HTTP Server started on port " + port);
        System.out.println("Repository: " + repository.getGitRoot());
        System.out.println("Available endpoints:");
        for (String route : router.getRoutes()) {
            System.out.println("  " + route);
        }
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleConnection(clientSocket);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handles a client connection.
     * 
     * @param clientSocket the client socket
     */
    private void handleConnection(Socket clientSocket) {
        try (InputStream inputStream = clientSocket.getInputStream();
             OutputStream outputStream = clientSocket.getOutputStream()) {
            
            // Parse the HTTP request
            Request request = RequestParser.parse(inputStream);
            System.out.println("Received: " + request.getMethod() + " " + request.getPath());
            
            // Route the request
            Response response = router.route(request);
            
            if (response == null) {
                response = Response.notFound("Not Found: " + request.getPath());
            }
            
            // Set connection header based on request
            if (request.isKeepAlive()) {
                response.setConnection("keep-alive");
            } else {
                response.setConnection("close");
            }
            
            // Write the response
            ResponseWriter.write(response, outputStream);
            System.out.println("Responded: " + response.getStatusCode() + " " + response.getStatusText());
            
        } catch (Exception e) {
            try {
                ResponseWriter.writeInternalServerError("Internal Server Error: " + e.getMessage(), 
                                                       clientSocket.getOutputStream());
            } catch (IOException ioException) {
                System.err.println("Error writing error response: " + ioException.getMessage());
            }
            System.err.println("Error handling request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stops the HTTP server.
     */
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
        System.out.println("Server stopped");
    }
    
    /**
     * Checks if the server is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Gets the server port.
     * 
     * @return the port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Gets the repository.
     * 
     * @return the repository
     */
    public Repository getRepository() {
        return repository;
    }
    
    /**
     * Main method to start the server.
     * 
     * @param args command line arguments (port, repository path)
     */
    public static void main(String[] args) {
        int port = 8080;
        String repositoryPath = ".";
        
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }
        
        if (args.length >= 2) {
            repositoryPath = args[1];
        }
        
        HttpServer server = new HttpServer(port, repositoryPath);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            server.stop();
        }));
        
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}

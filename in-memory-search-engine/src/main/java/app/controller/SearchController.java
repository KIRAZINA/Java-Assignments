package app.controller;

import app.model.Document;
import app.model.IndexStats;
import app.service.DocumentNotFoundException;
import app.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the in-memory search engine (Task 3 & 4).
 *
 * <p>Endpoints (all RESTful, with correct status codes):
 * <ul>
 *   <li>{@code POST /documents} - index a single document (400 if id/content missing).</li>
 *   <li>{@code POST /documents/batch} - index many documents atomically-ish in one call.</li>
 *   <li>{@code GET /search?q=...} - run a query, TF-IDF ranked (400 if query > 1000 chars).</li>
 *   <li>{@code GET /stats} - index statistics.</li>
 *   <li>{@code GET /documents/{id}} - fetch a document (404 if absent).</li>
 *   <li>{@code DELETE /documents/{id}} - remove a document (404 if absent).</li>
 * </ul>
 *
 * <p>All error responses are produced by {@link GlobalExceptionHandler}.
 */
@RestController
public class SearchController {

    private static final int MAX_QUERY_LENGTH = 1000;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/documents")
    public ResponseEntity<String> addDocument(@RequestBody Document document) {
        validate(document);
        searchService.addDocument(document);
        return ResponseEntity.ok("Document indexed: " + document.getId());
    }

    @PostMapping("/documents/batch")
    public ResponseEntity<Map<String, Object>> addDocuments(@RequestBody List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Document list must not be empty");
        }
        for (Document d : documents) {
            validate(d);
        }
        int indexed = searchService.addDocuments(documents);
        return ResponseEntity.ok(Map.of("indexed", indexed));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(@RequestParam("q") String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters");
        }
        return ResponseEntity.ok(searchService.search(query));
    }

    @GetMapping("/stats")
    public ResponseEntity<IndexStats> stats() {
        return ResponseEntity.ok(searchService.getStats());
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable String id) {
        Document doc = searchService.getDocument(id);
        if (doc == null) {
            throw new DocumentNotFoundException("Document not found: " + id);
        }
        return ResponseEntity.ok(doc);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable String id) {
        if (!searchService.removeDocument(id)) {
            throw new DocumentNotFoundException("Document not found: " + id);
        }
        return ResponseEntity.ok("Document deleted: " + id);
    }

    private void validate(Document doc) {
        if (doc == null || doc.getId() == null || doc.getId().isBlank()) {
            throw new IllegalArgumentException("Document id is required");
        }
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            throw new IllegalArgumentException("Document content is required");
        }
    }
}

package app.controller;

import app.model.Document;
import app.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/documents")
    public ResponseEntity<String> addDocument(@RequestBody Document document) {
        if (document.getId() == null || document.getId().isEmpty()) {
            return ResponseEntity.badRequest().body("Document id is required");
        }
        if (document.getContent() == null || document.getContent().isEmpty()) {
            return ResponseEntity.badRequest().body("Document content is required");
        }
        searchService.addDocument(document);
        return ResponseEntity.ok("Document indexed: " + document.getId());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(@RequestParam("q") String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<Document> results = searchService.search(query);
        return ResponseEntity.ok(results);
    }
}
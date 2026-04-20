package app;

import app.model.Document;
import app.service.SearchService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {

    @Test
    void testFullWorkflow() {
        SearchService searchService = new SearchService();

        searchService.addDocument(new Document("doc1", "Java Spring Boot is powerful"));
        searchService.addDocument(new Document("doc2", "Python Django is great"));
        searchService.addDocument(new Document("doc3", "Java Spring Boot with Python"));

        List<Document> results1 = searchService.search("java spring");
        assertEquals(2, results1.size());
        assertTrue(results1.stream().anyMatch(d -> d.getId().equals("doc1")));
        assertTrue(results1.stream().anyMatch(d -> d.getId().equals("doc3")));

        List<Document> results2 = searchService.search("java|python");
        assertEquals(3, results2.size());

        List<Document> results3 = searchService.search("\"spring boot\"");
        assertEquals(2, results3.size());

        List<Document> results4 = searchService.search("python");
        assertEquals(2, results4.size());
    }
}
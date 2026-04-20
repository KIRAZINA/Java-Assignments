package app;

import app.model.Document;
import app.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SearchServiceTest {
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService();
    }

    @Test
    void testAddAndSearchDocument() {
        Document doc = new Document("doc1", "Java Spring Boot is powerful");
        searchService.addDocument(doc);

        List<Document> results = searchService.search("java spring");
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getId());
    }

    @Test
    void testSearchWithNoResults() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot"));

        List<Document> results = searchService.search("python");
        assertTrue(results.isEmpty());
    }

    @Test
    void testOrSearch() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot"));
        searchService.addDocument(new Document("doc2", "Python Django"));
        searchService.addDocument(new Document("doc3", "Java Python"));

        List<Document> results = searchService.search("java|python");
        assertEquals(3, results.size());
    }

    @Test
    void testPhraseSearch() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot is powerful"));
        searchService.addDocument(new Document("doc2", "Spring Boot is great"));

        List<Document> results = searchService.search("\"spring boot\"");
        assertEquals(2, results.size());
    }

    @Test
    void testRanking() {
        searchService.addDocument(new Document("doc1", "java spring"));
        searchService.addDocument(new Document("doc2", "java spring python"));
        searchService.addDocument(new Document("doc3", "java"));

        List<Document> results = searchService.search("java spring");
        assertEquals("doc2", results.get(0).getId());
        assertEquals("doc1", results.get(1).getId());
    }

    @Test
    void testStopWordFilteringInQuery() {
        searchService.addDocument(new Document("doc1", "The Java is powerful"));

        List<Document> results = searchService.search("the java");
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getId());
    }

    @Test
    void testRankingNoSubstringMatches() {
        searchService.addDocument(new Document("doc1", "javascript"));
        searchService.addDocument(new Document("doc2", "java"));
        searchService.addDocument(new Document("doc3", "python"));

        List<Document> results = searchService.search("java");

        assertEquals(1, results.size());
        assertEquals("doc2", results.get(0).getId());
    }
}
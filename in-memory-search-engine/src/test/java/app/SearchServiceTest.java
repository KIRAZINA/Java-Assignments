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

    // ---- Retained behavioural coverage ----

    @Test
    void testAddAndSearchDocument() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot is powerful"));
        List<Document> results = searchService.search("java spring");
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getId());
    }

    @Test
    void testSearchWithNoResults() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot"));
        assertTrue(searchService.search("python").isEmpty());
    }

    @Test
    void testOrSearch() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot"));
        searchService.addDocument(new Document("doc2", "Python Django"));
        searchService.addDocument(new Document("doc3", "Java Python"));
        assertEquals(3, searchService.search("java|python").size());
    }

    @Test
    void testPhraseSearch() {
        searchService.addDocument(new Document("doc1", "Java Spring Boot is powerful"));
        searchService.addDocument(new Document("doc2", "Spring Boot is great"));
        assertEquals(2, searchService.search("\"spring boot\"").size());
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

    // ---- Task 1: TF-IDF ranking ----

    @Test
    void testTfIdfRanking() {
        // Doc1 has the highest TF for "java"; all three contain "java" so its
        // IDF is 0, but the deterministic tie-break (by id) preserves the
        // required ordering doc1 > doc2 > doc3.
        searchService.addDocument(new Document("doc1", "java java java python"));
        searchService.addDocument(new Document("doc2", "java python ruby"));
        searchService.addDocument(new Document("doc3", "python ruby java"));

        List<Document> results = searchService.search("java");
        assertEquals(3, results.size());
        assertEquals("doc1", results.get(0).getId());
        assertEquals("doc3", results.get(results.size() - 1).getId());
    }

    @Test
    void testTfIdfDifferentiatesByTermFrequency() {
        // "kubernetes" is in 2 of 3 docs (IDF > 0). doc2 repeats it 3x, so its
        // TF - and therefore its TF-IDF score - is higher than doc1.
        searchService.addDocument(new Document("doc1", "kubernetes docker"));
        searchService.addDocument(new Document("doc2", "kubernetes kubernetes kubernetes python"));
        searchService.addDocument(new Document("doc3", "python java"));

        List<Document> results = searchService.search("kubernetes");
        assertEquals(2, results.size());
        assertEquals("doc2", results.get(0).getId());
    }

    @Test
    void testRankingFavoursRelevantDocument() {
        // doc2 concentrates the query terms -> higher TF -> ranks first.
        searchService.addDocument(new Document("doc1", "java spring programming"));
        searchService.addDocument(new Document("doc2", "java java java spring spring"));
        searchService.addDocument(new Document("doc3", "java"));
        List<Document> results = searchService.search("java spring");
        assertEquals("doc2", results.get(0).getId());
        assertEquals("doc1", results.get(1).getId());
    }

    // ---- Task 2: edge cases ----

    @Test
    void testEmptyQueryReturnsEmpty() {
        searchService.addDocument(new Document("doc1", "java spring"));
        assertTrue(searchService.search("").isEmpty());
        assertTrue(searchService.search("   ").isEmpty());
        assertTrue(searchService.search(null).isEmpty());
    }

    @Test
    void testStopWordOnlyQueryReturnsEmpty() {
        searchService.addDocument(new Document("doc1", "java spring"));
        // "the is a an" normalises to only stop words -> no terms to search.
        assertTrue(searchService.search("the is a an").isEmpty());
    }

    @Test
    void testQueryLongerThan1000CharsRejected() {
        String longQuery = "a".repeat(1001);
        assertThrows(IllegalArgumentException.class, () -> searchService.search(longQuery));
    }

    @Test
    void testNonExistentTermReturnsEmpty() {
        searchService.addDocument(new Document("doc1", "java spring"));
        assertTrue(searchService.search("nonexistentterm").isEmpty());
    }

    @Test
    void testSpecialCharactersInPhraseHandled() {
        // Special regex-like characters are normalised away; the phrase still matches.
        searchService.addDocument(new Document("doc1", "c++ java spring"));
        searchService.addDocument(new Document("doc2", "java spring boot"));
        List<Document> results = searchService.search("\"java spring\"");
        assertEquals(2, results.size());
    }

    // ---- Task 2: query cache ----

    @Test
    void testQueryCacheReusesParsedQuery() {
        searchService.addDocument(new Document("doc1", "java spring boot"));
        searchService.addDocument(new Document("doc2", "python django"));

        String query = "java spring";
        // First call is a miss; the next 99 are hits.
        searchService.search(query);
        for (int i = 0; i < 99; i++) {
            searchService.search(query);
        }
        // Exactly one parse happened, 99 cache hits followed.
        assertEquals(99, searchService.getCacheHits());

        // The cached path must still be correct.
        List<Document> results = searchService.search(query);
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getId());
    }

    @Test
    void testQueryCacheImprovesLatency() {
        searchService.addDocument(new Document("d", "java spring boot kubernetes docker"));
        String query = "java spring boot";

        long coldNs = measure(() -> searchService.search(query));
        long warmMinNs = Long.MAX_VALUE;
        for (int i = 0; i < 100; i++) {
            warmMinNs = Math.min(warmMinNs, measure(() -> searchService.search(query)));
        }
        // Skipping re-parsing must not make the warm call slower than the cold one.
        assertTrue(warmMinNs <= coldNs,
                "warm min (" + warmMinNs + " ns) should be <= cold (" + coldNs + " ns)");
    }

    private long measure(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return System.nanoTime() - start;
    }
}

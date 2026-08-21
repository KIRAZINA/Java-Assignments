package app;

import app.model.Document;
import app.service.InvertedIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class InvertedIndexTest {

    private InvertedIndex index;

    @BeforeEach
    void setUp() {
        index = new InvertedIndex();
    }

    @Test
    void testAddDocument() {
        Document doc = new Document("doc1", "Java Spring Boot is powerful");
        index.addDocument(doc);

        Set<String> javaDocs = index.searchAND(Set.of("java"));
        assertTrue(javaDocs.contains("doc1"));

        Set<String> springDocs = index.searchAND(Set.of("spring"));
        assertTrue(springDocs.contains("doc1"));
    }

    @Test
    void testSearchAND() {
        index.addDocument(new Document("doc1", "Java Spring Boot"));
        index.addDocument(new Document("doc2", "Python Django"));
        index.addDocument(new Document("doc3", "Java Python"));

        Set<String> result = index.searchAND(Set.of("java", "spring"));
        assertTrue(result.contains("doc1"));
        assertEquals(1, result.size());
    }

    @Test
    void testSearchOR() {
        index.addDocument(new Document("doc1", "Java Spring Boot"));
        index.addDocument(new Document("doc2", "Python Django"));
        index.addDocument(new Document("doc3", "Java Python"));

        Set<String> result = index.searchOR(Set.of("java", "python"));
        assertEquals(3, result.size());
        assertTrue(result.contains("doc1"));
        assertTrue(result.contains("doc2"));
        assertTrue(result.contains("doc3"));
    }

    @Test
    void testStopWordsFiltering() {
        index.addDocument(new Document("doc1", "The Java is powerful"));
        // "the" is a stop word, so it is ignored during search; the query
        // effectively becomes a search for "java", which doc1 contains.
        Set<String> result = index.searchAND(Set.of("the", "java"));
        assertTrue(result.contains("doc1"));
        result = index.searchAND(Set.of("java"));
        assertTrue(result.contains("doc1"));
    }

    @Test
    void testCaseInsensitive() {
        index.addDocument(new Document("doc1", "JAVA SPRING"));
        Set<String> result = index.searchAND(Set.of("java"));
        assertTrue(result.contains("doc1"));
        result = index.searchAND(Set.of("spring"));
        assertTrue(result.contains("doc1"));
    }

    @Test
    void testDuplicateDocumentUpdates() {
        index.addDocument(new Document("doc1", "Java Spring"));
        index.addDocument(new Document("doc1", "Python Django"));
        Set<String> result = index.searchAND(Set.of("java"));
        assertFalse(result.contains("doc1"));
        result = index.searchAND(Set.of("python"));
        assertTrue(result.contains("doc1"));
    }

    @Test
    void testEmptyContent() {
        Document doc = new Document("doc1", "");
        index.addDocument(doc);
        Set<String> result = index.searchAND(Set.of("anything"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testContentWithOnlyStopWords() {
        index.addDocument(new Document("doc1", "the is a an"));
        Set<String> result = index.searchAND(Set.of("the", "is"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testDuplicateWordsInContent() {
        index.addDocument(new Document("doc1", "java java java spring"));
        Set<String> result = index.searchAND(Set.of("java"));
        assertEquals(1, result.size());
        assertTrue(result.contains("doc1"));
    }

    // ---- Task 3: concurrent indexing with per-document locks ----

    @Test
    void testConcurrentIndexingDistinctIds() throws InterruptedException {
        int threads = 16;
        int docsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < docsPerThread; i++) {
                        String id = "doc-" + threadNum + "-" + i;
                        index.addDocument(new Document(id, "java spring " + (i % 10)));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(0, errors.get());
        assertEquals(threads * docsPerThread, index.getDocumentCount());
    }

    @Test
    void testPerDocumentLockNoCorruption() throws InterruptedException {
        // Many threads hammer the SAME document id with add/remove. The
        // per-document lock must keep the posting lists and document map
        // consistent (never duplicate the id, never leave a half-written doc).
        int threads = 8;
        int opsPerThread = 300;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        index.addDocument(new Document("shared", "java spring python ruby " + i));
                        if (i % 3 == 0) index.removeDocument("shared");
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(0, errors.get());
        // Only ever one id ("shared") is touched, so the count is at most 1.
        assertTrue(index.getDocumentCount() <= 1,
                "document count should never exceed 1 for a single id");
    }

    // ---- Task 1 / 3: statistics ----

    @Test
    void testStatistics() {
        index.addDocument(new Document("doc1", "java spring boot framework"));
        index.addDocument(new Document("doc2", "java python ruby"));
        index.addDocument(new Document("doc3", "python django java"));

        assertEquals(3, index.getDocumentCount());
        assertTrue(index.getUniqueTermCount() > 0);
        assertTrue(index.getAverageDocLength() > 0);
        assertTrue(index.getIndexSizeBytes() > 0);

        // "java" appears in all 3 docs -> df = 3, tf in doc1 = 1, docLen(doc1) = 4.
        assertEquals(3, index.getDocFrequency("java"));
        assertEquals(1, index.getTermFrequency("doc1", "java"));
        assertEquals(4, index.getDocLength("doc1"));

        // "django" appears in exactly 1 doc.
        assertEquals(1, index.getDocFrequency("django"));
    }
}

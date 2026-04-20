package app;

import app.model.Document;
import app.service.InvertedIndex;
import app.service.QueryParser;
import app.service.TextProcessor;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class AdditionalTest {

    @Test
    void testTextProcessorNormalize() {
        assertEquals("", TextProcessor.normalize(null));
        assertEquals("", TextProcessor.normalize(""));
        assertEquals("hello world", TextProcessor.normalize("Hello World"));
        assertEquals("hello world", TextProcessor.normalize("Hello, World!"));
        assertEquals("hello world", TextProcessor.normalize("  Hello   World  "));
    }

    @Test
    void testTextProcessorTokenize() {
        Set<String> tokens = TextProcessor.tokenize("Java Spring Boot is powerful");
        assertTrue(tokens.contains("java"));
        assertTrue(tokens.contains("spring"));
        assertTrue(tokens.contains("boot"));
        assertTrue(tokens.contains("powerful"));
        assertFalse(tokens.contains("is"));
    }

    @Test
    void testTextProcessorContainsExactPhrase() {
        assertTrue(TextProcessor.containsExactPhrase("java spring boot", "spring boot"));
        assertFalse(TextProcessor.containsExactPhrase("java something boot", "spring boot"));
        assertFalse(TextProcessor.containsExactPhrase("boot spring", "spring boot"));
        assertTrue(TextProcessor.containsExactPhrase("spring boot", "spring boot"));
    }

    @Test
    void testTextProcessorContainsPhraseEdgeCases() {
        assertFalse(TextProcessor.containsExactPhrase("java spring", "spring boot"));
        assertTrue(TextProcessor.containsExactPhrase("java spring boot framework", "spring boot"));
    }

    @Test
    void testQueryParserAnd() {
        QueryParser parser = new QueryParser();
        
        var result = parser.parse("java spring");
        assertEquals(QueryParser.QueryType.AND, result.type());
        assertTrue(result.terms().contains("java"));
        assertTrue(result.terms().contains("spring"));
    }

    @Test
    void testQueryParserOr() {
        QueryParser parser = new QueryParser();
        
        var result = parser.parse("java|python");
        assertEquals(QueryParser.QueryType.OR, result.type());
        assertTrue(result.terms().contains("java"));
        assertTrue(result.terms().contains("python"));
    }

    @Test
    void testQueryParserPhrase() {
        QueryParser parser = new QueryParser();
        
        var result = parser.parse("\"spring boot\"");
        assertEquals(QueryParser.QueryType.PHRASE, result.type());
        assertEquals("spring boot", result.phrase());
    }

    @Test
    void testQueryParserEmptyAfterStopWords() {
        QueryParser parser = new QueryParser();
        
        var result = parser.parse("the is a an");
        assertTrue(result.terms().isEmpty());
    }

    @Test
    void testQueryParserWhitespace() {
        QueryParser parser = new QueryParser();
        
        var result = parser.parse("  java   spring  ");
        assertEquals(QueryParser.QueryType.AND, result.type());
        assertTrue(result.terms().contains("java"));
        assertTrue(result.terms().contains("spring"));
    }

    @Test
    void testQueryParserOrWithEmptyParts() {
        QueryParser parser = new QueryParser();
        
        var result = parser.parse("java| |python");
        assertEquals(QueryParser.QueryType.OR, result.type());
        assertEquals(2, result.terms().size());
    }

    @Test
    void testConcurrentIndexing() throws InterruptedException {
        InvertedIndex index = new InvertedIndex();
        int threads = 10;
        int docsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < docsPerThread; i++) {
                        String id = "doc-" + threadNum + "-" + i;
                        String content = "java spring " + (i % 10);
                        index.addDocument(new Document(id, content));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(0, errors.get());
        assertEquals(threads * docsPerThread, index.getDocumentCount());
    }

    @Test
    void testConcurrentSearch() throws InterruptedException {
        InvertedIndex index = new InvertedIndex();
        
        for (int i = 0; i < 100; i++) {
            index.addDocument(new Document("doc" + i, "java spring python " + i));
        }

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        Set<String> result = index.searchAND(Set.of("java"));
                        if (result.isEmpty()) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(0, errors.get());
    }

    @Test
    void testPhraseSearchWordBoundaries() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document("doc1", "spring boot framework"));
        index.addDocument(new Document("doc2", "boot spring framework"));
        index.addDocument(new Document("doc3", "spring boot"));

        Set<String> candidates = index.searchAND(Set.of("spring", "boot"));
        Set<String> result = index.searchPhrase("spring boot", candidates);
        
        assertEquals(2, result.size());
        assertTrue(result.contains("doc1"));
        assertTrue(result.contains("doc3"));
        assertFalse(result.contains("doc2"));
    }

    @Test
    void testSearchWithOnlyStopWordsInQuery() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document("doc1", "java spring"));

        Set<String> result = index.searchAND(Set.of("the", "is"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testAndSearchOrderDoesNotMatter() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document("doc1", "java spring"));

        Set<String> result1 = index.searchAND(Set.of("java", "spring"));
        Set<String> result2 = index.searchAND(Set.of("spring", "java"));
        
        assertEquals(result1, result2);
    }

    @Test
    void testOrSearchNoDuplicates() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document("doc1", "java spring"));
        index.addDocument(new Document("doc2", "java python"));
        index.addDocument(new Document("doc3", "python spring"));

        Set<String> result = index.searchOR(Set.of("java", "python"));
        assertEquals(3, result.size());
    }
}
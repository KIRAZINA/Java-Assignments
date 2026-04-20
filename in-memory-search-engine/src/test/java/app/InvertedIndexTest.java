package app;

import app.model.Document;
import app.service.InvertedIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
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

        Set<String> result = index.searchAND(Set.of("the", "java"));
        assertFalse(result.contains("doc1"));

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
}
package app.service;

import app.model.Document;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InvertedIndex {
    private final Map<String, Set<String>> index;
    private final Map<String, Document> documents;

    public InvertedIndex() {
        this.index = new ConcurrentHashMap<>();
        this.documents = new ConcurrentHashMap<>();
    }

    public void addDocument(Document doc) {
        if (doc == null || doc.getId() == null || doc.getId().isEmpty()) {
            throw new IllegalArgumentException("Document and document ID cannot be null or empty");
        }
        
        Document existing = documents.get(doc.getId());
        if (existing != null) {
            removeDocument(doc.getId());
        }
        
        documents.put(doc.getId(), doc);
        Set<String> tokens = TextProcessor.tokenize(doc.getContent());
        
        for (String token : tokens) {
            index.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet()).add(doc.getId());
        }
    }

    public void removeDocument(String docId) {
        Document doc = documents.remove(docId);
        if (doc != null) {
            Set<String> tokens = TextProcessor.tokenize(doc.getContent());
            for (String token : tokens) {
                Set<String> docIds = index.get(token);
                if (docIds != null) {
                    docIds.remove(docId);
                    if (docIds.isEmpty()) {
                        index.remove(token);
                    }
                }
            }
        }
    }

    public Set<String> searchAND(Set<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> normalizedTerms = new HashSet<>();
        for (String term : terms) {
            String processed = TextProcessor.normalize(term);
            if (!processed.isEmpty()) {
                normalizedTerms.add(processed);
            }
        }

        if (normalizedTerms.isEmpty()) {
            return Collections.emptySet();
        }

        Iterator<String> iterator = normalizedTerms.iterator();
        Set<String> result = new HashSet<>(index.getOrDefault(iterator.next(), Collections.emptySet()));

        while (iterator.hasNext()) {
            String term = iterator.next();
            Set<String> docIds = index.getOrDefault(term, Collections.emptySet());
            result.retainAll(docIds);
        }

        return result;
    }

    public Set<String> searchOR(Set<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> normalizedTerms = new HashSet<>();
        for (String term : terms) {
            String processed = TextProcessor.normalize(term);
            if (!processed.isEmpty()) {
                normalizedTerms.add(processed);
            }
        }

        if (normalizedTerms.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (String term : normalizedTerms) {
            Set<String> docIds = index.getOrDefault(term, Collections.emptySet());
            result.addAll(docIds);
        }

        return result;
    }

    public Set<String> searchPhrase(String phrase, Set<String> candidateIds) {
        Set<String> result = new HashSet<>();

        if (candidateIds != null && !candidateIds.isEmpty()) {
            for (String id : candidateIds) {
                Document doc = documents.get(id);
                if (doc != null && TextProcessor.containsExactPhrase(doc.getContent(), phrase)) {
                    result.add(id);
                }
            }
        } else {
            for (Map.Entry<String, Document> entry : documents.entrySet()) {
                if (TextProcessor.containsExactPhrase(entry.getValue().getContent(), phrase)) {
                    result.add(entry.getKey());
                }
            }
        }

        return result;
    }

    public Document getDocument(String id) {
        return documents.get(id);
    }

    public int getDocumentCount() {
        return documents.size();
    }
}
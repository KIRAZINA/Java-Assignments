package app.service;

import app.model.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe in-memory inverted index (built entirely from scratch, no Lucene).
 *
 * <p>Storage model:
 * <ul>
 *   <li>{@code termFreq}: term &rarr; (docId &rarr; raw term count in that doc).
 *       This posting list drives both boolean search (the inner keySet is the
 *       set of documents containing the term) and TF-IDF scoring (the count
 *       gives term frequency).</li>
 *   <li>{@code documents}: docId &rarr; Document.</li>
 *   <li>{@code docLengths}: docId &rarr; number of indexed terms (after stop-word
 *       removal), needed to normalise term frequency by document length.</li>
 * </ul>
 *
 * <p>CONCURRENCY (Task 3): Every mutation is guarded so the structure is safe
 * under concurrent load.
 * <ul>
 *   <li>The backing maps are {@link ConcurrentHashMap} instances, so independent
 *       concurrent puts/reads are lock-free and safe.</li>
 *   <li>For a given document ID we additionally take a dedicated
 *       {@link ReentrantLock} ("per-document lock"). This makes the compound
 *       "remove old version + insert new version" of {@link #addDocument}
 *       atomic with respect to that ID, without ever locking the whole index
 *       (which would serialise all writers and destroy throughput).</li>
 * </ul>
 */
@Component
public class InvertedIndex {

    /** term -> (docId -> raw count of term in that document). */
    private final Map<String, Map<String, Integer>> termFreq;
    /** docId -> Document. */
    private final Map<String, Document> documents;
    /** docId -> number of indexed terms. */
    private final Map<String, Integer> docLengths;
    /** Per-document re-entrant locks for atomic single-doc updates. */
    private final Map<String, ReentrantLock> docLocks;

    public InvertedIndex() {
        this.termFreq = new ConcurrentHashMap<>();
        this.documents = new ConcurrentHashMap<>();
        this.docLengths = new ConcurrentHashMap<>();
        this.docLocks = new ConcurrentHashMap<>();
    }

    /**
     * Add (or replace) a document.
     *
     * <p>ATOMICITY: the entire remove-old + insert-new sequence is performed
     * while holding the lock dedicated to this document ID, so concurrent
     * add/remove calls for the SAME id can never interleave and corrupt the
     * posting lists. The lock is always released in a {@code finally} block.
     */
    public void addDocument(Document doc) {
        if (doc == null || doc.getId() == null || doc.getId().isEmpty()) {
            throw new IllegalArgumentException("Document and document ID cannot be null or empty");
        }
        String id = doc.getId();
        ReentrantLock lock = docLocks.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock();
        try {
            // Remove any previous version of this document (also under the lock).
            removeDocumentInternal(id);
            documents.put(id, doc);

            List<String> tokens = TextProcessor.tokenizeToList(doc.getContent());
            docLengths.put(id, tokens.size());

            // Aggregate raw term counts for this document.
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) {
                counts.merge(token, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                termFreq.computeIfAbsent(e.getKey(), k -> new ConcurrentHashMap<>())
                        .put(id, e.getValue());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a document and detach it from every posting list.
     *
     * @return true if a document was actually removed.
     */
    public boolean removeDocument(String docId) {
        ReentrantLock lock = docLocks.computeIfAbsent(docId, k -> new ReentrantLock());
        lock.lock();
        try {
            boolean existed = documents.containsKey(docId);
            removeDocumentInternal(docId);
            return existed;
        } finally {
            lock.unlock();
        }
    }

    /** Internal removal without locking (caller must already hold the doc lock). */
    private void removeDocumentInternal(String docId) {
        Document doc = documents.remove(docId);
        docLengths.remove(docId);
        if (doc != null) {
            for (String token : TextProcessor.tokenize(doc.getContent())) {
                Map<String, Integer> postings = termFreq.get(token);
                if (postings != null) {
                    postings.remove(docId);
                    if (postings.isEmpty()) {
                        termFreq.remove(token);
                    }
                }
            }
        }
    }

    /** Boolean AND over (normalised) terms: documents containing ALL terms. */
    public Set<String> searchAND(Set<String> terms) {
        Set<String> normalized = normalizeTerms(terms);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }
        Iterator<String> it = normalized.iterator();
        // Seed with the document set of the first term, then intersect.
        Set<String> result = new HashSet<>(termFreq.getOrDefault(it.next(), Map.of()).keySet());
        while (it.hasNext()) {
            Set<String> docIds = termFreq.getOrDefault(it.next(), Map.of()).keySet();
            result.retainAll(docIds);
        }
        return result;
    }

    /** Boolean OR over (normalised) terms: documents containing ANY term. */
    public Set<String> searchOR(Set<String> terms) {
        Set<String> normalized = normalizeTerms(terms);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String term : normalized) {
            result.addAll(termFreq.getOrDefault(term, Map.of()).keySet());
        }
        return result;
    }

    /**
     * Phrase search: return ids whose content contains the exact (normalised)
     * phrase. Optionally restricted to a candidate set for efficiency.
     */
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

    /** Remove every document and reset all index structures. */
    public void clear() {
        termFreq.clear();
        documents.clear();
        docLengths.clear();
        docLocks.clear();
    }

    // ------------------------------------------------------------------
    // TF-IDF support (Task 1)
    // ------------------------------------------------------------------

    /** Number of documents containing {@code term} (document frequency, df). */
    public int getDocFrequency(String term) {
        return termFreq.getOrDefault(term, Map.of()).size();
    }

    /** Raw term count of {@code term} within document {@code docId}. */
    public int getTermFrequency(String docId, String term) {
        return termFreq.getOrDefault(term, Map.of()).getOrDefault(docId, 0);
    }

    /** Number of indexed terms in document {@code docId} (used to normalise TF). */
    public int getDocLength(String docId) {
        return docLengths.getOrDefault(docId, 0);
    }

    /** Total number of distinct terms in the whole index. */
    public int getUniqueTermCount() {
        return termFreq.size();
    }

    /** Mean document length (in terms) across all indexed documents. */
    public double getAverageDocLength() {
        if (docLengths.isEmpty()) return 0.0;
        long sum = 0;
        for (int len : docLengths.values()) sum += len;
        return (double) sum / docLengths.size();
    }

    /** Approximate memory footprint of the index in bytes (for /stats). */
    public long getIndexSizeBytes() {
        long size = 0;
        for (Map.Entry<String, Map<String, Integer>> e : termFreq.entrySet()) {
            size += (long) e.getKey().length() * 2L; // term chars (~2 bytes each)
            for (Map.Entry<String, Integer> inner : e.getValue().entrySet()) {
                size += (long) inner.getKey().length() * 2L + 4L; // docId + int count
            }
        }
        for (Map.Entry<String, Document> e : documents.entrySet()) {
            size += (long) e.getKey().length() * 2L;
            String content = e.getValue().getContent();
            if (content != null) size += (long) content.length() * 2L;
        }
        return size;
    }

    private Set<String> normalizeTerms(Set<String> terms) {
        Set<String> normalized = new HashSet<>();
        if (terms == null) return normalized;
        for (String term : terms) {
            String processed = TextProcessor.normalize(term);
            if (!processed.isEmpty() && !TextProcessor.STOP_WORDS.contains(processed)) {
                normalized.add(processed);
            }
        }
        return normalized;
    }
}

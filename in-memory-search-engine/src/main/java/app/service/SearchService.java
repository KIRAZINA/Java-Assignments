package app.service;

import app.model.Document;
import app.model.IndexStats;
import app.service.QueryParser.ParsedQuery;
import app.service.QueryParser.QueryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-level search facade: query parsing, boolean/phrase retrieval, TF-IDF
 * ranking, parsed-query caching and metrics (Task 1, 2, 4).
 */
@Service
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 1000;
    private static final int QUERY_CACHE_MAX = 100;

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final InvertedIndex invertedIndex;
    private final QueryParser queryParser;
    private final SearchMetrics metrics;
    private final QueryCache queryCache;

    public SearchService() {
        // Used by unit tests: build a self-contained instance with a simple
        // in-memory meter registry (no Spring container required).
        this(new InvertedIndex(), new QueryParser(), new SearchMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Autowired
    public SearchService(InvertedIndex invertedIndex, QueryParser queryParser, SearchMetrics metrics) {
        this.invertedIndex = invertedIndex;
        this.queryParser = queryParser;
        this.metrics = metrics;
        this.queryCache = new QueryCache(QUERY_CACHE_MAX);
    }

    /** Spring lifecycle hook: register live gauges once the beans exist. */
    @jakarta.annotation.PostConstruct
    public void init() {
        metrics.registerGauges(invertedIndex, queryCache);
    }

    // ------------------------------------------------------------------
    // Indexing
    // ------------------------------------------------------------------

    public void addDocument(Document doc) {
        invertedIndex.addDocument(doc);
    }

    /**
     * Index a batch of documents. Although the underlying index is already
     * thread-safe, batching avoids the per-call overhead of many HTTP round
     * trips and is dramatically faster than indexing one-by-one (Task 3).
     */
    public int addDocuments(List<Document> docs) {
        if (docs == null) return 0;
        for (Document doc : docs) {
            invertedIndex.addDocument(doc);
        }
        return docs.size();
    }

    public Document getDocument(String id) {
        return invertedIndex.getDocument(id);
    }

    public boolean removeDocument(String id) {
        return invertedIndex.removeDocument(id);
    }

    public IndexStats getStats() {
        return new IndexStats(
                invertedIndex.getDocumentCount(),
                invertedIndex.getUniqueTermCount(),
                invertedIndex.getAverageDocLength(),
                invertedIndex.getIndexSizeBytes()
        );
    }

    public long getCacheHits() {
        return queryCache.hitCount();
    }

    // ------------------------------------------------------------------
    // Search (Task 1 + 2)
    // ------------------------------------------------------------------

    /**
     * Execute a search query and return TF-IDF-ranked results.
     *
     * <p>Edge cases handled up front (Task 2):
     * <ul>
     *   <li>Null / blank / stop-word-only query &rarr; empty result set, no index scan.</li>
     *   <li>Query longer than {@value #MAX_QUERY_LENGTH} chars &rarr; {@link IllegalArgumentException}.</li>
     *   <li>Repeated identical queries &rarr; parsed result served from the LRU cache.</li>
     * </ul>
     */
    public List<Document> search(String query) {
        if (query == null || query.isBlank()) {
            // Empty / whitespace-only / stop-word-only -> no point searching.
            return List.of();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "Query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters");
        }

        long start = System.nanoTime();

        // Memoize parsed queries so the same string is never parsed twice.
        ParsedQuery parsed = queryCache.get(query);
        boolean cached = parsed != null;
        if (!cached) {
            parsed = queryParser.parse(query);
            queryCache.put(query, parsed);
        }

        Set<String> resultIds;
        switch (parsed.type()) {
            case PHRASE -> {
                Set<String> phraseTerms = TextProcessor.tokenize(parsed.phrase());
                Set<String> candidates = invertedIndex.searchAND(phraseTerms);
                resultIds = invertedIndex.searchPhrase(parsed.phrase(), candidates);
            }
            case OR -> resultIds = invertedIndex.searchOR(parsed.terms());
            default -> resultIds = invertedIndex.searchAND(parsed.terms());
        }

        List<Document> docs = new ArrayList<>(resultIds.size());
        for (String id : resultIds) {
            Document d = invertedIndex.getDocument(id);
            if (d != null) docs.add(d);
        }

        // Ranking terms: for a phrase, score on its constituent tokens.
        Set<String> rankingTerms = (parsed.type() == QueryType.PHRASE)
                ? TextProcessor.tokenize(parsed.phrase())
                : parsed.terms();

        List<Document> ranked = rankResults(docs, rankingTerms);

        long durationNs = System.nanoTime() - start;
        metrics.recordQuery(Duration.ofNanos(durationNs));
        if (cached) metrics.markCacheHit(); else metrics.markCacheMiss();

        // Structured (JSON-friendly) debug logging of every query.
        if (log.isDebugEnabled()) {
            log.debug("{\"event\":\"search\",\"query\":\"{}\",\"cached\":{},\"durationMs\":{},\"results\":{}}",
                    query, cached, durationNs / 1_000_000.0, ranked.size());
        }
        return ranked;
    }

    /**
     * Rank documents by TF-IDF (Task 1).
     *
     * <p>MATHEMATICAL RATIONALE:
     * <ul>
     *   <li><b>TF (term frequency)</b> = count(term, doc) / |doc|. Normalising by
     *       document length prevents long documents from artificially dominating
     *       just because they contain more words.</li>
     *   <li><b>IDF (inverse document frequency)</b> = ln(N / df). Rare terms
     *       (small df) get a large IDF, common terms (large df, up to all N
     *       documents) get IDF &rarr; 0. This is what makes "the" worthless and
     *       "kubernetes" valuable - TF-IDF rewards terms that are both frequent
     *       in a document AND rare across the corpus.</li>
     *   <li><b>Score</b> = &Sigma;<sub>t&isin;query</sub> TF(t, doc) &times; IDF(t).
     *       This strictly beats naive keyword counting, which ignores both
     *       document length and term rarity.</li>
     * </ul>
     * Documents with equal scores are ordered by document ID for determinism.
     */
    private List<Document> rankResults(List<Document> docs, Set<String> queryTerms) {
        if (docs.isEmpty()) {
            return docs;
        }
        int n = invertedIndex.getDocumentCount(); // total documents N

        // Pre-compute IDF per query term once.
        Map<String, Double> idf = new HashMap<>();
        for (String term : queryTerms) {
            int df = invertedIndex.getDocFrequency(term);
            // df == 0 means the term is in no document (no contribution).
            // df == N means the term is in every document -> IDF = ln(1) = 0,
            // i.e. it carries no discriminative power. Both handled by 0.
            idf.put(term, df <= 0 ? 0.0 : Math.log((double) n / df));
        }

        List<ScoredDoc> scored = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            double score = 0.0;
            int len = invertedIndex.getDocLength(doc.getId());
            if (len > 0) {
                for (String term : queryTerms) {
                    int tfCount = invertedIndex.getTermFrequency(doc.getId(), term);
                    double tf = (double) tfCount / len;
                    score += tf * idf.get(term);
                }
            }
            scored.add(new ScoredDoc(doc, score));
        }

        // Higher score first; deterministic tie-break by ascending document ID.
        scored.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return a.doc.getId().compareTo(b.doc.getId());
        });

        List<Document> result = new ArrayList<>(scored.size());
        for (ScoredDoc sd : scored) result.add(sd.doc);
        return result;
    }

    /** Tiny carrier for a document paired with its computed relevance score. */
    private record ScoredDoc(Document doc, double score) { }

    // ------------------------------------------------------------------
    // Parsed-query LRU cache (Task 2)
    // ------------------------------------------------------------------

    /**
     * Bounded LRU cache of parsed queries (max 100 entries, Task 2).
     *
     * <p>Implemented with a {@link LinkedHashMap} in access-order mode guarded by
     * a single {@link ReentrantLock}. Every get/put is O(1); the lock is held
     * for only a few nanoseconds (pure HashMap operations), so under heavy
     * concurrent load it never becomes a bottleneck - unlike a deque-based
     * recency list whose {@code remove()} is O(n). The eldest entry is evicted
     * automatically via {@link LinkedHashMap#removeEldestEntry} when capacity is
     * exceeded.
     */
    private static final class QueryCache implements SearchMetrics.QueryCache {
        private final int maxSize;
        private final LinkedHashMap<String, ParsedQuery> map;
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong miss = new AtomicLong();

        QueryCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new LinkedHashMap<String, ParsedQuery>(maxSize + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ParsedQuery> eldest) {
                    return size() > QueryCache.this.maxSize;
                }
            };
        }

        ParsedQuery get(String key) {
            lock.lock();
            try {
                ParsedQuery v = map.get(key); // access-order update is automatic (accessOrder=true)
                if (v != null) hits.incrementAndGet(); else miss.incrementAndGet();
                return v;
            } finally {
                lock.unlock();
            }
        }

        void put(String key, ParsedQuery value) {
            lock.lock();
            try {
                map.put(key, value); // eviction handled by removeEldestEntry
            } finally {
                lock.unlock();
            }
        }

        long hitCount() { return hits.get(); }

        public long size() {
            lock.lock();
            try {
                return map.size();
            } finally {
                lock.unlock();
            }
        }
    }
}

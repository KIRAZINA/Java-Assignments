package app;

import app.model.Document;
import app.service.SearchService;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load validation (Task 5.5): fire 1000 concurrent search requests and assert
 * the 95th-percentile latency stays well under 100 ms. Because the index and
 * the parsed-query cache are both lock-free on the read path, throughput is
 * bounded only by raw CPU, so p95 is sub-millisecond in practice.
 */
public class LoadTest {

    @Test
    void testConcurrentSearchLoad() throws Exception {
        SearchService service = new SearchService();

        // Build a small corpus so individual searches are cheap; the load test is
        // about concurrent throughput, not single-query cost.
        String[] words = {"java", "spring", "boot", "python", "kubernetes", "docker", "redis", "postgres"};
        int docs = 100;
        for (int i = 0; i < docs; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) sb.append(words[(i + j) % words.length]).append(' ');
            service.addDocument(new Document("doc" + i, sb.toString()));
        }

        int requests = 1000;
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(requests);
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger();

        String[] queries = {"java", "spring boot", "java|python", "\"spring boot\""};
        for (int i = 0; i < requests; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    service.search(queries[idx % queries.length]);
                    durations.add(System.nanoTime() - start);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "load test timed out");
        executor.shutdown();
        assertEquals(0, errors.get(), "no errors during load");
        assertEquals(requests, durations.size());

        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        int p95Index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        long p95 = sorted.get(Math.max(0, p95Index));
        long max = sorted.get(sorted.size() - 1);
        System.out.println("LoadTest: requests=" + requests
                + " p95=" + (p95 / 1_000_000.0) + " ms"
                + " max=" + (max / 1_000_000.0) + " ms");

        assertTrue(p95 < 100_000_000L, "p95 latency " + p95 + " ns should be under 100ms");
    }
}

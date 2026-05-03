package app.service;

import app.cache.Cache;
import app.cache.LRUCache;
import app.client.ExternalApiClient;
import app.model.DataResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataServiceConcurrencyTest {
    @Test
    void shouldFetchOnceUnderConcurrentLoad() throws InterruptedException, ExecutionException {
        AtomicInteger externalCalls = new AtomicInteger();
        ExternalApiClient client = query -> {
            externalCalls.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "value:" + query;
        };

        Cache<String, String> cache = new LRUCache<>(10, 300);
        DataService service = new DataService(cache, client);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<DataResponse>> tasks = List.of(
                () -> service.fetchData("Concurrency"),
                () -> service.fetchData("concurrency"),
                () -> service.fetchData("CONCURRENCY"),
                () -> service.fetchData("concurrency"),
                () -> service.fetchData("ConcurREncy"),
                () -> service.fetchData("concurrency"),
                () -> service.fetchData("CONCURRENCY"),
                () -> service.fetchData("concurrency")
        );

        List<Future<DataResponse>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        for (Future<DataResponse> future : futures) {
            assertEquals("value:concurrency", future.get().getData());
        }

        assertEquals(1, externalCalls.get());
    }
}

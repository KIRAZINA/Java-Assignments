package app.service;

import app.cache.Cache;
import app.cache.LRUCache;
import app.client.ExternalApiClient;
import app.model.DataResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DataServiceTest {
    @Test
    void shouldCallExternalApiOnceAndReturnCachedOnSecondRequest() {
        AtomicInteger externalCalls = new AtomicInteger();
        ExternalApiClient client = query -> {
            externalCalls.incrementAndGet();
            return "value:" + query;
        };

        Cache<String, String> cache = new LRUCache<>(10, 300);
        DataService service = new DataService(cache, client);

        DataResponse first = service.fetchData("hello");
        DataResponse second = service.fetchData("hello");

        assertEquals("value:hello", first.getData());
        assertFalse(first.isCached());
        assertEquals("value:hello", second.getData());
        assertTrue(second.isCached());
        assertEquals(1, externalCalls.get());
    }

    @Test
    void shouldStoreResultOnCacheMiss() {
        AtomicInteger externalCalls = new AtomicInteger();
        ExternalApiClient client = query -> {
            externalCalls.incrementAndGet();
            return "value:" + query;
        };

        Cache<String, String> cache = new LRUCache<>(10, 300);
        DataService service = new DataService(cache, client);

        DataResponse response = service.fetchData("world");

        assertEquals("value:world", response.getData());
        assertFalse(response.isCached());
        assertEquals(1, externalCalls.get());
        assertEquals("value:world", cache.get("world"));
    }

    @Test
    void shouldExpireDataInServiceAfterTtlAndRefetch() throws InterruptedException {
        AtomicInteger externalCalls = new AtomicInteger();
        ExternalApiClient client = query -> {
            externalCalls.incrementAndGet();
            return "value:" + query;
        };

        Cache<String, String> cache = new LRUCache<>(10, 1, LRUCache.ExpirationPolicy.WRITE);
        DataService service = new DataService(cache, client);

        DataResponse first = service.fetchData("timeout");
        Thread.sleep(1100);
        DataResponse second = service.fetchData("timeout");

        assertEquals("value:timeout", first.getData());
        assertEquals("value:timeout", second.getData());
        assertFalse(first.isCached());
        assertFalse(second.isCached());
        assertEquals(2, externalCalls.get());
    }

    @Test
    void shouldNormalizeKeysAndTreatWhitespaceCaseInsensitive() {
        AtomicInteger externalCalls = new AtomicInteger();
        ExternalApiClient client = query -> {
            externalCalls.incrementAndGet();
            return "value:" + query;
        };

        Cache<String, String> cache = new LRUCache<>(10, 300);
        DataService service = new DataService(cache, client);

        DataResponse first = service.fetchData(" Test ");
        DataResponse second = service.fetchData("test");
        DataResponse third = service.fetchData("TEST");

        assertEquals("value:test", first.getData());
        assertFalse(first.isCached());
        assertEquals("value:test", second.getData());
        assertTrue(second.isCached());
        assertEquals("value:test", third.getData());
        assertTrue(third.isCached());
        assertEquals(1, externalCalls.get());
    }
}

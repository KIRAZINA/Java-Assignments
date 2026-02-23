package com.example.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A thread-safe in-memory key-value store that supports basic CRUD operations,
 * JSON persistence, automatic snapshots, and Time-to-Live (TTL) for keys.
 *
 * <h2>TTL (Time-to-Live) Support</h2>
 * <p>Keys can have an optional expiration time. Expired entries are automatically
 * removed during read operations and by a background cleaner thread.
 * <ul>
 *   <li>Use {@link #put(K, V, long)} to set a TTL in seconds</li>
 *   <li>Use {@link #get(K)} - returns null for expired entries</li>
 *   <li>Use {@link #getRemainingTTL(K)} to check time until expiration</li>
 * </ul>
 *
 * <h2>Generics Limitations</h2>
 * <p>Due to Java type erasure, this store has limitations when deserializing generic types.
 * Gson uses runtime type information, which means:
 * <ul>
 *   <li>For simple types (String, Integer, Long, etc.): Works correctly out of the box</li>
 *   <li>For POJOs with no-arg constructors: Works correctly out of the box</li>
 *   <li>For complex generic types (List<T>, Map<K,V>): Requires explicit TypeToken in constructor</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. Read operations are non-blocking.
 * Write operations use ConcurrentHashMap's internal locking.
 * TTL operations are atomic with respect to get/put operations.
 *
 * @param <K> the type of keys maintained by this store
 * @param <V> the type of mapped values
 * @author Java Assignments Team
 * @version 3.0
 */
public class InMemoryKeyValueStore<K, V> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long NO_TTL = -1L;
    private static final long DEFAULT_CLEANER_INTERVAL_SECONDS = 60L;

    /**
     * Internal entry wrapper that stores value and expiration timestamp.
     *
     * @param <T> the type of the value
     */
    public record Entry<T>(T value, Long expiresAtEpochMillis) {
        
        /**
         * Checks if this entry has expired.
         *
         * @return true if the entry has expired, false otherwise
         */
        public boolean isExpired() {
            return expiresAtEpochMillis != null && 
                   System.currentTimeMillis() > expiresAtEpochMillis;
        }

        /**
         * Checks if this entry has a TTL set.
         *
         * @return true if TTL is set, false otherwise
         */
        public boolean hasTTL() {
            return expiresAtEpochMillis != null;
        }

        /**
         * Returns the remaining TTL in seconds.
         *
         * @return remaining seconds, or -1 if no TTL or already expired
         */
        public long getRemainingTTLSeconds() {
            if (expiresAtEpochMillis == null) {
                return -1;
            }
            long remaining = expiresAtEpochMillis - System.currentTimeMillis();
            return remaining > 0 ? TimeUnit.MILLISECONDS.toSeconds(remaining) : -1;
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> store;
    private final Type typeToken;
    private volatile ScheduledExecutorService snapshotExecutor;
    private volatile ScheduledExecutorService cleanerExecutor;
    private final Object snapshotLock = new Object();
    private final Object cleanerLock = new Object();
    private volatile long defaultTTLSeconds = NO_TTL;
    private volatile long cleanerIntervalSeconds;

    /**
     * Constructs an empty in-memory store with default type handling and
     * default cleaner interval (60 seconds).
     */
    public InMemoryKeyValueStore() {
        this(DEFAULT_CLEANER_INTERVAL_SECONDS);
    }

    /**
     * Constructs an empty in-memory store with specified cleaner interval.
     *
     * @param cleanerIntervalSeconds interval for background expired entries cleanup
     */
    public InMemoryKeyValueStore(long cleanerIntervalSeconds) {
        this.store = new ConcurrentHashMap<>();
        this.typeToken = new TypeToken<Map<K, Entry<V>>>() {}.getType();
        this.cleanerIntervalSeconds = cleanerIntervalSeconds;
    }

    /**
     * Constructs an empty in-memory store with explicit type token for proper
     * deserialization of complex generic types.
     *
     * @param typeToken the TypeToken describing the map type for JSON deserialization
     */
    @SuppressWarnings("unchecked")
    public InMemoryKeyValueStore(TypeToken<? extends Map<K, Entry<V>>> typeToken) {
        this.store = new ConcurrentHashMap<>();
        this.typeToken = typeToken.getType();
        this.cleanerIntervalSeconds = DEFAULT_CLEANER_INTERVAL_SECONDS;
    }

    /**
     * Constructs an empty in-memory store with explicit type token and cleaner interval.
     *
     * @param typeToken the TypeToken describing the map type for JSON deserialization
     * @param cleanerIntervalSeconds interval for background expired entries cleanup
     */
    @SuppressWarnings("unchecked")
    public InMemoryKeyValueStore(TypeToken<? extends Map<K, Entry<V>>> typeToken, long cleanerIntervalSeconds) {
        this.store = new ConcurrentHashMap<>();
        this.typeToken = typeToken.getType();
        this.cleanerIntervalSeconds = cleanerIntervalSeconds;
    }

    // ==================== TTL Configuration ====================

    /**
     * Sets a default TTL for all future put operations that don't specify an explicit TTL.
     *
     * @param ttlSeconds default TTL in seconds, or -1 to disable default TTL
     */
    public void setDefaultTTL(long ttlSeconds) {
        this.defaultTTLSeconds = ttlSeconds > 0 ? ttlSeconds : NO_TTL;
    }

    /**
     * Returns the current default TTL setting.
     *
     * @return default TTL in seconds, or -1 if not set
     */
    public long getDefaultTTL() {
        return defaultTTLSeconds;
    }

    /**
     * Sets the interval for the background cleaner thread.
     * Changes take effect after restarting the cleaner.
     *
     * @param intervalSeconds cleaner interval in seconds
     */
    public void setCleanerInterval(long intervalSeconds) {
        if (intervalSeconds > 0) {
            this.cleanerIntervalSeconds = intervalSeconds;
        }
    }

    // ==================== Basic CRUD Operations ====================

    /**
     * Maps the specified key to the specified value without TTL.
     * Uses default TTL if set via {@link #setDefaultTTL(long)}.
     *
     * @param key   key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (must not be null)
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V put(K key, V value) {
        return put(key, value, defaultTTLSeconds);
    }

    /**
     * Maps the specified key to the specified value with a TTL.
     *
     * @param key        key with which the specified value is to be associated (must not be null)
     * @param value      value to be associated with the specified key (must not be null)
     * @param ttlSeconds time-to-live in seconds; use -1 for no expiration
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V put(K key, V value, long ttlSeconds) {
        validateNotNull(key, "Key");
        validateNotNull(value, "Value");

        Long expiresAt = ttlSeconds > 0 
            ? System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds) 
            : null;

        Entry<V> entry = new Entry<>(value, expiresAt);
        Entry<V> previous = store.put(key, entry);
        return previous != null && !previous.isExpired() ? previous.value() : null;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or null if this store contains no mapping for the key or the entry has expired.
     *
     * <p>If the entry has expired, it is removed from the store.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or null if no mapping exists or expired
     */
    public V get(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key, entry); // Atomic removal if not changed
            return null;
        }
        return entry.value();
    }

    /**
     * Returns an {@link Optional} containing the value to which the specified key is mapped,
     * or an empty Optional if this store contains no mapping for the key or the entry has expired.
     *
     * @param key the key whose associated value is to be returned
     * @return an Optional containing the mapped value, or empty Optional if no mapping exists or expired
     */
    public Optional<V> getOptional(K key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or the specified defaultValue if this store contains no mapping for the key or the entry has expired.
     *
     * @param key          the key whose associated value is to be returned
     * @param defaultValue the value to return if no mapping exists for the key (must not be null)
     * @return the value to which the specified key is mapped, or defaultValue if no mapping exists or expired
     * @throws IllegalArgumentException if defaultValue is null
     */
    public V getOrDefault(K key, V defaultValue) {
        validateNotNull(defaultValue, "DefaultValue");
        V value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the remaining TTL for the specified key.
     *
     * @param key the key to check
     * @return remaining TTL in seconds, or -1 if no TTL set, key doesn't exist, or entry has expired
     */
    public long getRemainingTTL(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            return -1;
        }
        return entry.getRemainingTTLSeconds();
    }

    /**
     * Checks if the specified key has a TTL set.
     *
     * @param key the key to check
     * @return true if the key exists and has a TTL, false otherwise
     */
    public boolean hasTTL(K key) {
        Entry<V> entry = store.get(key);
        return entry != null && !entry.isExpired() && entry.hasTTL();
    }

    /**
     * Removes the mapping for a key from this store if it is present.
     *
     * @param key key whose mapping is to be removed from the store
     * @return true if the store contained a mapping for the key (and it wasn't expired), false otherwise
     */
    public boolean remove(K key) {
        Entry<V> entry = store.remove(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Returns true if this store contains a mapping for the specified key
     * and the entry has not expired.
     *
     * @param key key whose presence in this store is to be tested
     * @return true if this store contains a non-expired mapping for the specified key
     */
    public boolean containsKey(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            store.remove(key, entry);
            return false;
        }
        return true;
    }

    // ==================== Atomic Operations ====================

    /**
     * If the specified key is not already associated with a value (or is expired),
     * associates it with the given value using default TTL.
     *
     * @param key   key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (must not be null)
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value) {
        return putIfAbsent(key, value, defaultTTLSeconds);
    }

    /**
     * If the specified key is not already associated with a value (or is expired),
     * associates it with the given value with specified TTL.
     *
     * @param key        key with which the specified value is to be associated (must not be null)
     * @param value      value to be associated with the specified key (must not be null)
     * @param ttlSeconds time-to-live in seconds; use -1 for no expiration
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value, long ttlSeconds) {
        validateNotNull(key, "Key");
        validateNotNull(value, "Value");

        Long expiresAt = ttlSeconds > 0 
            ? System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds) 
            : null;

        Entry<V> newEntry = new Entry<>(value, expiresAt);
        
        while (true) {
            Entry<V> existing = store.get(key);
            if (existing == null || existing.isExpired()) {
                Entry<V> prev = store.putIfAbsent(key, newEntry);
                if (prev == null) {
                    return null;
                }
                // Another thread inserted, check if expired
                if (prev.isExpired()) {
                    if (store.replace(key, prev, newEntry)) {
                        return null;
                    }
                    // Retry
                    continue;
                }
                return prev.value();
            }
            return existing.value();
        }
    }

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function.
     *
     * @param key             key with which the computed value is to be associated (must not be null)
     * @param mappingFunction the function to compute a value (must not return null)
     * @return the current (existing or computed) value associated with the specified key
     * @throws IllegalArgumentException if the specified key is null
     * @throws NullPointerException     if the mappingFunction is null or returns null
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsent(key, mappingFunction, defaultTTLSeconds);
    }

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function with specified TTL.
     *
     * @param key             key with which the computed value is to be associated (must not be null)
     * @param mappingFunction the function to compute a value (must not return null)
     * @param ttlSeconds      time-to-live in seconds; use -1 for no expiration
     * @return the current (existing or computed) value associated with the specified key
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction, long ttlSeconds) {
        validateNotNull(key, "Key");
        if (mappingFunction == null) {
            throw new NullPointerException("Mapping function cannot be null.");
        }

        while (true) {
            Entry<V> existing = store.get(key);
            if (existing != null && !existing.isExpired()) {
                return existing.value();
            }

            V newValue = mappingFunction.apply(key);
            validateNotNull(newValue, "Computed value");

            Long expiresAt = ttlSeconds > 0 
                ? System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds) 
                : null;

            Entry<V> newEntry = new Entry<>(newValue, expiresAt);

            if (existing == null) {
                Entry<V> prev = store.putIfAbsent(key, newEntry);
                if (prev == null) {
                    return newValue;
                }
                if (prev.isExpired()) {
                    if (store.replace(key, prev, newEntry)) {
                        return newValue;
                    }
                    continue;
                }
                return prev.value();
            } else {
                if (store.replace(key, existing, newEntry)) {
                    return newValue;
                }
                // Retry
            }
        }
    }

    /**
     * If the value for the specified key is present and not expired,
     * attempts to compute a new mapping given the key and its current mapped value.
     *
     * @param key               key with which the specified value is to be associated (must not be null)
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     */
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        validateNotNull(key, "Key");
        if (remappingFunction == null) {
            throw new NullPointerException("Remapping function cannot be null.");
        }

        while (true) {
            Entry<V> existing = store.get(key);
            if (existing == null || existing.isExpired()) {
                if (existing != null) {
                    store.remove(key, existing);
                }
                return null;
            }

            V newValue = remappingFunction.apply(key, existing.value());
            
            if (newValue == null) {
                if (store.remove(key, existing)) {
                    return null;
                }
                continue;
            }

            validateNotNull(newValue, "Remapped value");
            Entry<V> newEntry = new Entry<>(newValue, existing.expiresAtEpochMillis());
            
            if (store.replace(key, existing, newEntry)) {
                return newValue;
            }
            // Retry
        }
    }

    /**
     * Returns an unmodifiable snapshot of the keys contained in this store.
     * Expired entries are excluded.
     *
     * @return an unmodifiable set of the keys contained in this store
     */
    public Set<K> keySet() {
        removeExpired();
        return Set.copyOf(store.keySet());
    }

    // ==================== Store Management ====================

    /**
     * Returns the number of non-expired key-value mappings in this store.
     *
     * @return the number of non-expired key-value mappings in this store
     */
    public int size() {
        return (int) store.entrySet().stream()
            .filter(e -> !e.getValue().isExpired())
            .count();
    }

    /**
     * Returns true if this store contains no non-expired key-value mappings.
     *
     * @return true if this store contains no non-expired key-value mappings
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Removes all of the mappings from this store.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Manually removes all expired entries from the store.
     * This is useful for testing or when immediate cleanup is needed.
     *
     * @return the number of entries removed
     */
    public int removeExpired() {
        int removed = 0;
        for (Map.Entry<K, Entry<V>> entry : store.entrySet()) {
            if (entry.getValue().isExpired()) {
                if (store.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                }
            }
        }
        return removed;
    }

    // ==================== Persistence Operations ====================

    /**
     * Serializes the entire store to a file at the given path in JSON format.
     * Expired entries are excluded from serialization.
     *
     * @param filePath path to the file where the store will be saved
     * @throws StorePersistenceException if an IO error occurs during saving
     */
    public void saveToFile(String filePath) throws StorePersistenceException {
        saveToFile(filePath, true);
    }

    /**
     * Serializes the entire store to a file at the given path in JSON format.
     *
     * @param filePath           path to the file where the store will be saved
     * @param excludeExpired     if true, expired entries are not saved
     * @throws StorePersistenceException if an IO error occurs during saving
     */
    public void saveToFile(String filePath, boolean excludeExpired) throws StorePersistenceException {
        try (Writer writer = new FileWriter(filePath)) {
            Map<K, Entry<V>> toSave;
            if (excludeExpired) {
                toSave = store.entrySet().stream()
                    .filter(e -> !e.getValue().isExpired())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            } else {
                toSave = store;
            }
            GSON.toJson(toSave, writer);
        } catch (IOException e) {
            throw new StorePersistenceException("Failed to save store to file: " + filePath, e);
        }
    }

    /**
     * Deserializes the store from the file at the given path and loads it into memory.
     * Overwrites existing data in memory.
     *
     * @param filePath path to the file from which the store will be loaded
     * @throws StorePersistenceException if an error occurs during loading
     */
    public void loadFromFile(String filePath) throws StorePersistenceException {
        loadFromFile(filePath, false);
    }

    /**
     * Deserializes the store from the file at the given path and loads it into memory.
     *
     * @param filePath path to the file from which the store will be loaded
     * @param merge    if true, existing data is preserved and new data is merged
     * @throws StorePersistenceException if an error occurs during loading
     */
    @SuppressWarnings("unchecked")
    public void loadFromFile(String filePath, boolean merge) throws StorePersistenceException {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(filePath)) {
            Map<K, Entry<V>> loadedData = GSON.fromJson(reader, typeToken);

            if (loadedData != null) {
                for (Map.Entry<K, Entry<V>> entry : loadedData.entrySet()) {
                    if (entry.getKey() == null) {
                        throw new StorePersistenceException(
                            "Loaded data contains null keys, which are not allowed in this store.");
                    }
                    if (entry.getValue() == null || entry.getValue().value() == null) {
                        throw new StorePersistenceException(
                            "Loaded data contains null values for key '" + entry.getKey() +
                            "', which are not allowed in this store.");
                    }
                }

                if (!merge) {
                    store.clear();
                }
                // Filter out already-expired entries during load
                loadedData.entrySet().stream()
                    .filter(e -> !e.getValue().isExpired())
                    .forEach(e -> store.put(e.getKey(), e.getValue()));
            }
        } catch (JsonSyntaxException e) {
            throw new StorePersistenceException(
                "Failed to parse JSON from file: " + filePath + ". The file may be corrupted.", e);
        } catch (IOException e) {
            throw new StorePersistenceException("Failed to load store from file: " + filePath, e);
        }
    }

    // ==================== Auto-Snapshot Feature ====================

    /**
     * Starts a background thread to save the store to the specified file at regular intervals.
     *
     * @param filePath         path where the snapshot will be saved
     * @param intervalSeconds interval between snapshots in seconds (must be positive)
     * @throws IllegalArgumentException if intervalSeconds is not positive
     */
    public void startAutoSnapshot(String filePath, long intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("Interval must be positive.");
        }

        synchronized (snapshotLock) {
            stopAutoSnapshot();

            snapshotExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "StoreAutoSnapshotThread");
                t.setDaemon(true);
                return t;
            });

            snapshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    saveToFile(filePath);
                } catch (StorePersistenceException e) {
                    System.err.println("Auto-snapshot failed: " + e.getMessage());
                }
            }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Stops the background snapshot thread if it is running.
     */
    public void stopAutoSnapshot() {
        synchronized (snapshotLock) {
            if (snapshotExecutor != null && !snapshotExecutor.isShutdown()) {
                snapshotExecutor.shutdown();
                try {
                    if (!snapshotExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        snapshotExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    snapshotExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                snapshotExecutor = null;
            }
        }
    }

    /**
     * Checks if auto-snapshot is currently running.
     *
     * @return true if auto-snapshot is active, false otherwise
     */
    public boolean isAutoSnapshotRunning() {
        synchronized (snapshotLock) {
            return snapshotExecutor != null && !snapshotExecutor.isShutdown();
        }
    }

    // ==================== Background Cleaner ====================

    /**
     * Starts the background cleaner thread that periodically removes expired entries.
     */
    public void startCleaner() {
        synchronized (cleanerLock) {
            stopCleaner();

            cleanerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "StoreCleanerThread");
                t.setDaemon(true);
                return t;
            });

            cleanerExecutor.scheduleAtFixedRate(
                this::removeExpired,
                cleanerIntervalSeconds,
                cleanerIntervalSeconds,
                TimeUnit.SECONDS
            );
        }
    }

    /**
     * Stops the background cleaner thread if it is running.
     */
    public void stopCleaner() {
        synchronized (cleanerLock) {
            if (cleanerExecutor != null && !cleanerExecutor.isShutdown()) {
                cleanerExecutor.shutdown();
                try {
                    if (!cleanerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleanerExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanerExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                cleanerExecutor = null;
            }
        }
    }

    /**
     * Checks if the background cleaner is currently running.
     *
     * @return true if cleaner is active, false otherwise
     */
    public boolean isCleanerRunning() {
        synchronized (cleanerLock) {
            return cleanerExecutor != null && !cleanerExecutor.isShutdown();
        }
    }

    /**
     * Stops all background threads (snapshot and cleaner).
     */
    public void shutdown() {
        stopAutoSnapshot();
        stopCleaner();
    }

    // ==================== Private Helper Methods ====================

    private void validateNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null.");
        }
    }
}

package com.example.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A thread-safe in-memory key-value store that supports basic CRUD operations,
 * JSON persistence, and automatic snapshots.
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
 * <h2>Recommended Usage</h2>
 * <p>For most use cases, use simple key types (String recommended) and POJO values:
 * <pre>{@code
 * // Simple usage with String keys and POJO values
 * InMemoryKeyValueStore<String, User> store = new InMemoryKeyValueStore<>();
 *
 * // For complex generic types, provide TypeToken
 * TypeToken<Map<String, List<User>>> typeToken = new TypeToken<>() {};
 * InMemoryKeyValueStore<String, List<User>> store = new InMemoryKeyValueStore<>(typeToken);
 * }</pre>
 *
 * <h2>Null Handling</h2>
 * <p>Neither keys nor values can be null. This is a limitation of {@link ConcurrentHashMap}
 * which is used internally for thread-safety.
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. Read operations are non-blocking.
 * Write operations use ConcurrentHashMap's internal locking.
 * Atomic operations ({@link #putIfAbsent}, {@link #computeIfAbsent}, {@link #computeIfPresent})
 * provide atomicity for compound operations.
 *
 * @param <K> the type of keys maintained by this store
 * @param <V> the type of mapped values
 * @author Java Assignments Team
 * @version 2.1
 */
public class InMemoryKeyValueStore<K, V> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ConcurrentHashMap<K, V> store;
    private final Type typeToken;
    private volatile ScheduledExecutorService snapshotExecutor;
    private final Object snapshotLock = new Object();

    /**
     * Constructs an empty in-memory store with default type handling.
     *
     * <p>This constructor is suitable for simple types (String, Integer, POJOs).
     * For complex generic types, use {@link #InMemoryKeyValueStore(TypeToken)}.
     */
    public InMemoryKeyValueStore() {
        this.store = new ConcurrentHashMap<>();
        this.typeToken = new TypeToken<Map<K, V>>() {}.getType();
    }

    /**
     * Constructs an empty in-memory store with explicit type token for proper
     * deserialization of complex generic types.
     *
     * <p>Use this constructor when your value type contains generics:
     * <pre>{@code
     * TypeToken<Map<String, List<User>>> token = new TypeToken<>() {};
     * InMemoryKeyValueStore<String, List<User>> store = new InMemoryKeyValueStore<>(token);
     * }</pre>
     *
     * @param typeToken the TypeToken describing the map type for JSON deserialization
     */
    @SuppressWarnings("unchecked")
    public InMemoryKeyValueStore(TypeToken<? extends Map<K, V>> typeToken) {
        this.store = new ConcurrentHashMap<>();
        this.typeToken = typeToken.getType();
    }

    // ==================== Basic CRUD Operations ====================

    /**
     * Maps the specified key to the specified value in this store.
     * If the store previously contained a mapping for the key, the old value is replaced.
     *
     * <p><b>Thread Safety:</b> This operation is thread-safe and uses
     * {@link ConcurrentHashMap#put(Object, Object)} internally.
     *
     * @param key   key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (must not be null)
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V put(K key, V value) {
        validateNotNull(key, "Key");
        validateNotNull(value, "Value");
        return store.put(key, value);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or null if this store contains no mapping for the key.
     *
     * <p><b>Thread Safety:</b> This operation is non-blocking and thread-safe.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or null if no mapping exists
     */
    public V get(K key) {
        return store.get(key);
    }

    /**
     * Returns an {@link Optional} containing the value to which the specified key is mapped,
     * or an empty Optional if this store contains no mapping for the key.
     *
     * <p>This method is preferred over {@link #get(Object)} when you need to distinguish
     * between "key not found" and "value is null" (though null values are not allowed in this store).
     *
     * <p><b>Thread Safety:</b> This operation is non-blocking and thread-safe.
     *
     * @param key the key whose associated value is to be returned
     * @return an Optional containing the mapped value, or empty Optional if no mapping exists
     */
    public Optional<V> getOptional(K key) {
        return Optional.ofNullable(store.get(key));
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or the specified defaultValue if this store contains no mapping for the key.
     *
     * <p><b>Thread Safety:</b> This operation is non-blocking and thread-safe.
     *
     * @param key          the key whose associated value is to be returned
     * @param defaultValue the value to return if no mapping exists for the key (must not be null)
     * @return the value to which the specified key is mapped, or defaultValue if no mapping exists
     * @throws IllegalArgumentException if defaultValue is null
     */
    public V getOrDefault(K key, V defaultValue) {
        validateNotNull(defaultValue, "DefaultValue");
        return store.getOrDefault(key, defaultValue);
    }

    /**
     * Removes the mapping for a key from this store if it is present.
     *
     * <p><b>Thread Safety:</b> This operation is atomic and thread-safe.
     *
     * @param key key whose mapping is to be removed from the store
     * @return true if the store contained a mapping for the key, false otherwise
     */
    public boolean remove(K key) {
        return store.remove(key) != null;
    }

    /**
     * Returns true if this store contains a mapping for the specified key.
     *
     * <p><b>Thread Safety:</b> This operation is non-blocking and thread-safe.
     *
     * @param key key whose presence in this store is to be tested
     * @return true if this store contains a mapping for the specified key
     */
    public boolean containsKey(K key) {
        return store.containsKey(key);
    }

    // ==================== Atomic Operations ====================

    /**
     * If the specified key is not already associated with a value,
     * associates it with the given value.
     *
     * <p><b>Thread Safety:</b> This operation is atomic. If multiple threads attempt
     * to putIfAbsent for the same key concurrently, only one will succeed.
     *
     * @param key   key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (must not be null)
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value) {
        validateNotNull(key, "Key");
        validateNotNull(value, "Value");
        return store.putIfAbsent(key, value);
    }

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this map.
     *
     * <p><b>Thread Safety:</b> This operation is atomic. The mapping function
     * is executed at most once per key, even under high concurrency.
     *
     * <p><b>Note:</b> The mapping function must not attempt to modify this map
     * and should return a non-null value.
     *
     * @param key             key with which the computed value is to be associated (must not be null)
     * @param mappingFunction the function to compute a value (must not return null)
     * @return the current (existing or computed) value associated with the specified key
     * @throws IllegalArgumentException if the specified key is null
     * @throws NullPointerException     if the mappingFunction is null or returns null
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        validateNotNull(key, "Key");
        if (mappingFunction == null) {
            throw new NullPointerException("Mapping function cannot be null.");
        }
        return store.computeIfAbsent(key, mappingFunction);
    }

    /**
     * If the value for the specified key is present, attempts to compute a new mapping
     * given the key and its current mapped value.
     *
     * <p><b>Thread Safety:</b> This operation is atomic. The remapping function
     * is executed atomically with respect to other operations on the same key.
     *
     * <p><b>Note:</b> The remapping function must not attempt to modify this map.
     * If the remapping function returns null, the mapping is removed.
     *
     * @param key               key with which the specified value is to be associated (must not be null)
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws IllegalArgumentException if the specified key is null
     * @throws NullPointerException     if the remappingFunction is null
     */
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        validateNotNull(key, "Key");
        if (remappingFunction == null) {
            throw new NullPointerException("Remapping function cannot be null.");
        }
        return store.computeIfPresent(key, remappingFunction);
    }

    /**
     * Returns an unmodifiable snapshot of the keys contained in this store.
     *
     * <p>The returned set is a copy and will not reflect subsequent changes to the store.
     * This method uses {@link Set#copyOf} for immutability.
     *
     * <p><b>Thread Safety:</b> This operation creates a consistent snapshot
     * but is not atomic with respect to other operations.
     *
     * @return an unmodifiable set of the keys contained in this store
     */
    public Set<K> keySet() {
        return Set.copyOf(store.keySet());
    }

    // ==================== Store Management ====================

    /**
     * Returns the number of key-value mappings in this store.
     *
     * <p><b>Thread Safety:</b> This operation is non-blocking but the returned value
     * may be stale if concurrent modifications are in progress.
     *
     * @return the number of key-value mappings in this store
     */
    public int size() {
        return store.size();
    }

    /**
     * Returns true if this store contains no key-value mappings.
     *
     * <p><b>Thread Safety:</b> This operation is non-blocking but the returned value
     * may be stale if concurrent modifications are in progress.
     *
     * @return true if this store contains no key-value mappings
     */
    public boolean isEmpty() {
        return store.isEmpty();
    }

    /**
     * Removes all of the mappings from this store.
     *
     * <p><b>Thread Safety:</b> This operation is thread-safe but not atomic
     * with respect to other operations.
     */
    public void clear() {
        store.clear();
    }

    // ==================== Persistence Operations ====================

    /**
     * Serializes the entire store to a file at the given path in JSON format.
     *
     * <p><b>Thread Safety:</b> This operation creates a consistent snapshot
     * but concurrent modifications during save may or may not be included.
     *
     * @param filePath path to the file where the store will be saved
     * @throws StorePersistenceException if an IO error occurs during saving
     */
    public void saveToFile(String filePath) throws StorePersistenceException {
        try (Writer writer = new FileWriter(filePath)) {
            GSON.toJson(store, writer);
        } catch (IOException e) {
            throw new StorePersistenceException("Failed to save store to file: " + filePath, e);
        }
    }

    /**
     * Deserializes the store from the file at the given path and loads it into memory.
     * Overwrites existing data in memory.
     *
     * <p>If the file does not exist, this method does nothing and returns silently.
     *
     * <p><b>Thread Safety:</b> This operation clears and repopulates the store.
     * Concurrent reads during this operation may see inconsistent data.
     *
     * @param filePath path to the file from which the store will be loaded
     * @throws StorePersistenceException if an error occurs during loading,
     *         the file contains invalid JSON, or contains null keys/values
     */
    public void loadFromFile(String filePath) throws StorePersistenceException {
        loadFromFile(filePath, false);
    }

    /**
     * Deserializes the store from the file at the given path and loads it into memory.
     *
     * <p>If the file does not exist, this method does nothing and returns silently.
     *
     * <p><b>Thread Safety:</b> This operation modifies the store.
     * Concurrent reads during this operation may see inconsistent data.
     *
     * @param filePath path to the file from which the store will be loaded
     * @param merge    if true, existing data is preserved and new data is merged;
     *                 if false, existing data is cleared before loading
     * @throws StorePersistenceException if an error occurs during loading,
     *         the file contains invalid JSON, or contains null keys/values
     */
    @SuppressWarnings("unchecked")
    public void loadFromFile(String filePath, boolean merge) throws StorePersistenceException {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(filePath)) {
            Map<K, V> loadedData = GSON.fromJson(reader, typeToken);

            if (loadedData != null) {
                // Validate no null keys or values
                for (Map.Entry<K, V> entry : loadedData.entrySet()) {
                    if (entry.getKey() == null) {
                        throw new StorePersistenceException(
                            "Loaded data contains null keys, which are not allowed in this store.");
                    }
                    if (entry.getValue() == null) {
                        throw new StorePersistenceException(
                            "Loaded data contains null values for key '" + entry.getKey() +
                            "', which are not allowed in this store.");
                    }
                }

                if (!merge) {
                    store.clear();
                }
                store.putAll(loadedData);
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
     * <p>If auto-snapshot is already running, it will be stopped before starting a new one,
     * ensuring only one snapshot executor is active at a time.
     *
     * <p>The snapshot thread is created as a daemon thread, so it will not prevent
     * the JVM from shutting down.
     *
     * <p><b>Thread Safety:</b> This method is synchronized to ensure only one
     * snapshot executor runs at a time.
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
            // Always stop any existing executor before starting a new one
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
     *
     * <p>This method waits up to 5 seconds for the executor to terminate gracefully.
     * If it doesn't terminate in time, it will be forcefully shut down.
     *
     * <p><b>Thread Safety:</b> This method is safe to call from any thread
     * and can be called multiple times.
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

    // ==================== Private Helper Methods ====================

    /**
     * Validates that the given value is not null.
     *
     * @param value the value to check
     * @param name  the name of the parameter for the error message
     * @throws IllegalArgumentException if value is null
     */
    private void validateNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null.");
        }
    }
}

package com.example.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A thread-safe in-memory key-value store that supports basic CRUD operations,
 * JSON persistence, and automatic snapshots.
 *
 * <p><b>Supported Types:</b> This store uses Gson for serialization. Keys and values
 * should be types that Gson can serialize/deserialize natively:
 * <ul>
 *   <li>Primitive wrappers (Integer, Long, Double, Boolean, etc.)</li>
 *   <li>String</li>
 *   <li>POJOs with a no-arg constructor and standard getters/setters</li>
 *   <li>Arrays and Collections of the above types</li>
 * </ul>
 *
 * <p><b>Null Handling:</b> Null keys are not permitted and will throw
 * {@link IllegalArgumentException}. Null values are allowed for flexibility.
 *
 * <p><b>Thread Safety:</b> All operations are thread-safe, leveraging
 * {@link ConcurrentHashMap}'s atomic operations where applicable.
 *
 * @param <K> the type of keys maintained by this store
 * @param <V> the type of mapped values
 * @author Java Assignments Team
 * @version 2.0
 */
public class InMemoryKeyValueStore<K, V> {

    private final ConcurrentHashMap<K, V> store;
    private final Gson gson;
    private volatile ScheduledExecutorService snapshotExecutor;
    private final Object snapshotLock = new Object();

    /**
     * Constructs an empty in-memory store.
     */
    public InMemoryKeyValueStore() {
        this.store = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    }

    // ==================== Basic CRUD Operations ====================

    /**
     * Maps the specified key to the specified value in this store.
     * If the store previously contained a mapping for the key, the old value is replaced.
     *
     * <p><b>Note:</b> Neither keys nor values can be null, as this store uses
     * {@link ConcurrentHashMap} internally which does not support null values.
     *
     * @param key   key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (must not be null)
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null.");
        }
        return store.put(key, value);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or null if this store contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or null if no mapping exists
     */
    public V get(K key) {
        return store.get(key);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or the specified defaultValue if this store contains no mapping for the key.
     *
     * @param key          the key whose associated value is to be returned
     * @param defaultValue the value to return if no mapping exists for the key
     * @return the value to which the specified key is mapped, or defaultValue if no mapping exists
     */
    public V getOrDefault(K key, V defaultValue) {
        return store.getOrDefault(key, defaultValue);
    }

    /**
     * Removes the mapping for a key from this store if it is present.
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
     * @param key key whose presence in this store is to be tested
     * @return true if this store contains a mapping for the specified key
     */
    public boolean containsKey(K key) {
        return store.containsKey(key);
    }

    // ==================== Additional Atomic Operations ====================

    /**
     * If the specified key is not already associated with a value,
     * associates it with the given value.
     *
     * <p>This operation is atomic and thread-safe.
     *
     * <p><b>Note:</b> Neither keys nor values can be null.
     *
     * @param key   key with which the specified value is to be associated (must not be null)
     * @param value value to be associated with the specified key (must not be null)
     * @return the previous value associated with the key, or null if there was no mapping
     * @throws IllegalArgumentException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null.");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null.");
        }
        return store.putIfAbsent(key, value);
    }

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this map.
     *
     * <p>This operation is atomic and thread-safe, leveraging
     * {@link ConcurrentHashMap#computeIfAbsent(Object, Function)}.
     *
     * @param key             key with which the computed value is to be associated (must not be null)
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with the specified key
     * @throws IllegalArgumentException if the specified key is null
     * @throws NullPointerException     if the mappingFunction is null
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null.");
        }
        return store.computeIfAbsent(key, mappingFunction);
    }

    /**
     * Returns an unmodifiable snapshot of the keys contained in this store.
     *
     * <p>The returned set is a snapshot and will not reflect subsequent
     * changes to the store.
     *
     * @return an unmodifiable set of the keys contained in this store
     */
    public Set<K> keySet() {
        return Collections.unmodifiableSet(new HashSet<>(store.keySet()));
    }

    // ==================== Store Management ====================

    /**
     * Returns the number of key-value mappings in this store.
     *
     * @return the number of key-value mappings in this store
     */
    public int size() {
        return store.size();
    }

    /**
     * Returns true if this store contains no key-value mappings.
     *
     * @return true if this store contains no key-value mappings
     */
    public boolean isEmpty() {
        return store.isEmpty();
    }

    /**
     * Removes all of the mappings from this store.
     */
    public void clear() {
        store.clear();
    }

    // ==================== Persistence Operations ====================

    /**
     * Serializes the entire store to a file at the given path in JSON format.
     *
     * @param filePath path to the file where the store will be saved
     * @throws StorePersistenceException if an IO error occurs during saving
     */
    public void saveToFile(String filePath) throws StorePersistenceException {
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(store, writer);
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
     * @param filePath path to the file from which the store will be loaded
     * @throws StorePersistenceException if an error occurs during loading or if the file contains invalid JSON
     */
    public void loadFromFile(String filePath) throws StorePersistenceException {
        loadFromFile(filePath, false);
    }

    /**
     * Deserializes the store from the file at the given path and loads it into memory.
     *
     * <p>If the file does not exist, this method does nothing and returns silently.
     *
     * @param filePath path to the file from which the store will be loaded
     * @param merge    if true, existing data is preserved and new data is merged;
     *                 if false, existing data is cleared before loading
     * @throws StorePersistenceException if an error occurs during loading or if the file contains invalid JSON
     */
    @SuppressWarnings("unchecked")
    public void loadFromFile(String filePath, boolean merge) throws StorePersistenceException {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(filePath)) {
            // Use Map type to avoid Gson's internal LinkedTreeMap issues with generics
            Type type = new TypeToken<Map<K, V>>() {}.getType();
            Map<K, V> loadedData = gson.fromJson(reader, type);

            if (loadedData != null) {
                if (!merge) {
                    store.clear();
                }
                // Validate that no null keys are present in loaded data
                for (K key : loadedData.keySet()) {
                    if (key == null) {
                        throw new StorePersistenceException(
                            "Loaded data contains null keys, which are not allowed in this store.");
                    }
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
}

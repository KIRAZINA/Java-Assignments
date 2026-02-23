# In-Memory Key-Value Store

A thread-safe, generic In-Memory Key-Value Store implementation in Java with JSON persistence, automatic snapshots, and Time-to-Live (TTL) support.

## Features

- **Generic Types**: Supports any Key and Value types that are Gson-compatible.
- **Thread-Safety**: Uses `ConcurrentHashMap` for efficient concurrent access with atomic operations.
- **JSON Persistence**: Save and load the store's state to/from a JSON file using Google Gson.
- **Auto-Snapshots**: Background task to periodically save the store's state to a file.
- **TTL (Time-to-Live)**: Automatic expiration of entries with background cleaner thread.
- **Atomic Operations**: Includes `putIfAbsent`, `computeIfAbsent`, `computeIfPresent` for thread-safe conditional operations.
- **Optional Support**: `getOptional()` method for safe null-handling.
- **Clean Architecture**: Follows standard Maven project structure and clean code principles.
- **Comprehensive Testing**: 63 JUnit tests with Awaitility for async testing.

## TTL (Time-to-Live) Support

Keys can have an optional expiration time, making this store suitable for caching scenarios:

```java
InMemoryKeyValueStore<String, User> store = new InMemoryKeyValueStore<>();

// Put with 1 hour TTL
store.put("session:abc123", user, 3600);

// Check remaining TTL
long remaining = store.getRemainingTTL("session:abc123");

// Check if key has TTL
if (store.hasTTL("session:abc123")) {
    // Key will expire automatically
}

// Set default TTL for all future puts
store.setDefaultTTL(300); // 5 minutes
store.put("temp:data", data); // Uses default TTL

// Start background cleaner (removes expired entries periodically)
store.startCleaner();

// Manual cleanup
int removed = store.removeExpired();
```

### TTL Behavior

- **Automatic Expiration**: Expired entries return `null` on `get()` and are automatically removed
- **Background Cleaner**: Optional thread that periodically removes expired entries
- **Persistence Integration**: Expired entries are excluded from `saveToFile()` by default
- **Atomic Operations**: TTL-aware `putIfAbsent` and `computeIfAbsent`

## Generics Limitations

Due to Java type erasure, this store has limitations when deserializing generic types:

### Simple Types (Recommended)
For simple types, use the default constructor:
```java
// Works correctly out of the box
InMemoryKeyValueStore<String, User> store = new InMemoryKeyValueStore<>();
InMemoryKeyValueStore<String, Integer> store = new InMemoryKeyValueStore<>();
```

### Complex Generic Types
For complex generic types (e.g., `List<User>`, `Map<String, Object>`), provide a TypeToken:
```java
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;

// For List<User> values
TypeToken<Map<String, List<User>>> token = new TypeToken<>() {};
InMemoryKeyValueStore<String, List<User>> store = new InMemoryKeyValueStore<>(token);
```

### Why This Matters
Gson uses runtime type information for deserialization. Without explicit TypeToken:
- `List<User>` may deserialize as `List<LinkedTreeMap>` instead of `List<User>`
- Nested generics will lose their type information

## Supported Types

This store uses Gson for serialization. Keys and values should be types that Gson can serialize/deserialize natively:

- **Primitive wrappers**: `Integer`, `Long`, `Double`, `Boolean`, `Float`, `Short`, `Byte`, `Character`
- **String**
- **POJOs**: Classes with a no-arg constructor and standard getters/setters
- **Arrays and Collections**: Of the above types

> ⚠️ **Note**: For complex generic types, always use the TypeToken constructor.

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Build

Run the following command to build the project and run tests:

```bash
mvn clean install
```

## Usage Examples

### Basic CRUD Operations

```java
import com.example.store.InMemoryKeyValueStore;
import com.example.store.StorePersistenceException;

public class Main {
    public static void main(String[] args) throws StorePersistenceException {
        InMemoryKeyValueStore<String, Integer> store = new InMemoryKeyValueStore<>();

        // Basic put and get
        store.put("user:1:age", 25);
        Integer age = store.get("user:1:age");
        System.out.println("Age: " + age);

        // Get with default value
        Integer height = store.getOrDefault("user:1:height", 170);
        System.out.println("Height: " + height);

        // Check if key exists
        if (store.containsKey("user:1:age")) {
            System.out.println("Age is stored");
        }

        // Remove a key
        boolean removed = store.remove("user:1:age");
        System.out.println("Removed: " + removed);
    }
}
```

### Atomic Operations

```java
InMemoryKeyValueStore<String, Integer> store = new InMemoryKeyValueStore<>();

// putIfAbsent - only sets value if key doesn't exist
store.putIfAbsent("counter", 0);
Integer previous = store.putIfAbsent("counter", 100); // Returns 0, value stays 0

// computeIfAbsent - compute value lazily and atomically
Integer value = store.computeIfAbsent("expensive-key", k -> {
    // This computation only happens if the key is absent
    return calculateExpensiveValue(k);
});
```

### Working with Custom Objects

```java
public class User {
    private String name;
    private int age;
    
    // Required: no-arg constructor for Gson
    public User() {}
    
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    // Getters and setters...
}

// Usage
InMemoryKeyValueStore<String, User> userStore = new InMemoryKeyValueStore<>();
userStore.put("user:1", new User("Alice", 30));
User user = userStore.get("user:1");
```

### Persistence

```java
InMemoryKeyValueStore<String, String> store = new InMemoryKeyValueStore<>();
store.put("key1", "value1");
store.put("key2", "value2");

// Save to file
store.saveToFile("store.json");

// Load from file (overwrites existing data)
InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
newStore.loadFromFile("store.json");

// Load and merge with existing data
InMemoryKeyValueStore<String, String> mergeStore = new InMemoryKeyValueStore<>();
mergeStore.put("existing", "data");
mergeStore.loadFromFile("store.json", true); // merge=true preserves existing data
```

### Auto-Snapshot

```java
InMemoryKeyValueStore<String, String> store = new InMemoryKeyValueStore<>();

// Start auto-snapshot every 60 seconds
store.startAutoSnapshot("snapshot.json", 60);

// Check if auto-snapshot is running
if (store.isAutoSnapshotRunning()) {
    System.out.println("Auto-snapshot is active");
}

// Stop snapshotting when shutting down
store.stopAutoSnapshot();
```

### Getting All Keys

```java
InMemoryKeyValueStore<String, Integer> store = new InMemoryKeyValueStore<>();
store.put("a", 1);
store.put("b", 2);
store.put("c", 3);

// Get an unmodifiable snapshot of all keys
Set<String> keys = store.keySet();
System.out.println("Keys: " + keys); // [a, b, c]

// Note: The set is a snapshot and won't reflect later changes
store.put("d", 4);
// keys still contains [a, b, c]
```

## API Reference

### Basic Operations

| Method | Description |
|--------|-------------|
| `put(K key, V value)` | Associates the specified value with the specified key |
| `get(K key)` | Returns the value for the specified key, or null |
| `getOptional(K key)` | Returns an Optional containing the value, or empty Optional |
| `getOrDefault(K key, V defaultValue)` | Returns the value or defaultValue if not found |
| `remove(K key)` | Removes the mapping for the specified key |
| `containsKey(K key)` | Returns true if the store contains the specified key |
| `size()` | Returns the number of key-value mappings |
| `isEmpty()` | Returns true if the store is empty |
| `clear()` | Removes all mappings |
| `keySet()` | Returns an unmodifiable snapshot of all keys |

### Atomic Operations

| Method | Description |
|--------|-------------|
| `putIfAbsent(K key, V value)` | Associates value with key only if not already present |
| `putIfAbsent(K key, V value, long ttlSeconds)` | Same with TTL |
| `computeIfAbsent(K key, Function mapper)` | Computes value if key is absent |
| `computeIfAbsent(K key, Function mapper, long ttlSeconds)` | Same with TTL |
| `computeIfPresent(K key, BiFunction mapper)` | Computes new value if key is present; can remove entry by returning null |

### TTL Operations

| Method | Description |
|--------|-------------|
| `put(K key, V value, long ttlSeconds)` | Associates value with TTL in seconds |
| `getRemainingTTL(K key)` | Returns remaining TTL in seconds, or -1 if no TTL |
| `hasTTL(K key)` | Returns true if key has TTL set |
| `setDefaultTTL(long ttlSeconds)` | Sets default TTL for future puts |
| `removeExpired()` | Manually removes all expired entries |
| `startCleaner()` | Starts background cleaner thread |
| `stopCleaner()` | Stops background cleaner thread |
| `isCleanerRunning()` | Checks if cleaner is active |
| `shutdown()` | Stops all background threads |

### Persistence Operations

| Method | Description |
|--------|-------------|
| `saveToFile(String filePath)` | Saves the store to a JSON file |
| `loadFromFile(String filePath)` | Loads the store from a JSON file (overwrites) |
| `loadFromFile(String filePath, boolean merge)` | Loads with option to merge with existing data |

### Auto-Snapshot Operations

| Method | Description |
|--------|-------------|
| `startAutoSnapshot(String filePath, long intervalSeconds)` | Starts periodic snapshots |
| `stopAutoSnapshot()` | Stops the auto-snapshot thread |
| `isAutoSnapshotRunning()` | Returns true if auto-snapshot is active |

## Running Tests

The project includes comprehensive JUnit tests covering:

- Basic CRUD operations
- Null handling
- Atomic operations
- Concurrency scenarios
- Persistence (save/load/merge)
- Auto-snapshot functionality
- Edge cases and error handling

```bash
mvn test
```

## Thread Safety

All operations are thread-safe:

- **Read operations**: Non-blocking, using `ConcurrentHashMap`'s lock-free reads
- **Write operations**: Use `ConcurrentHashMap`'s internal locking for thread safety
- **Atomic operations**: `putIfAbsent` and `computeIfAbsent` are atomic
- **Auto-snapshot**: Uses a dedicated single-thread executor with proper synchronization

## Error Handling

The store uses `StorePersistenceException` for all persistence-related errors:

```java
try {
    store.saveToFile("data.json");
} catch (StorePersistenceException e) {
    System.err.println("Failed to save: " + e.getMessage());
    if (e.getCause() != null) {
        e.getCause().printStackTrace();
    }
}
```

Common scenarios that throw `StorePersistenceException`:

- File cannot be written (permission denied, disk full)
- File cannot be read (permission denied)
- JSON parsing fails (corrupted file)
- Loaded data contains null keys

## Null Handling

- **Null keys**: Not allowed, will throw `IllegalArgumentException`
- **Null values**: Not allowed (due to `ConcurrentHashMap` limitations), will throw `IllegalArgumentException`

> ⚠️ **Note**: `ConcurrentHashMap` does not support null keys or values. If you need to represent "no value", consider using an optional wrapper or a sentinel value.

## Dependencies

- **Gson 2.10.1**: JSON serialization
- **JUnit Jupiter 5.10.0**: Testing framework
- **Awaitility 4.2.0**: Async testing support

## License

This project is part of the Java Assignments repository for educational purposes.

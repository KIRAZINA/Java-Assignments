# In-Memory Key-Value Store

A thread-safe, generic In-Memory Key-Value Store implementation in Java with JSON persistence and automatic snapshots.

## Features

- **Generic Types**: Supports any Key and Value types that are Gson-compatible.
- **Thread-Safety**: Uses `ConcurrentHashMap` for efficient concurrent access with atomic operations.
- **JSON Persistence**: Save and load the store's state to/from a JSON file using Google Gson.
- **Auto-Snapshots**: Background task to periodically save the store's state to a file.
- **Atomic Operations**: Includes `putIfAbsent`, `computeIfAbsent` for thread-safe conditional operations.
- **Clean Architecture**: Follows standard Maven project structure and clean code principles.
- **Comprehensive Testing**: Over 25 JUnit tests with Awaitility for async testing.

## Supported Types

This store uses Gson for serialization. Keys and values should be types that Gson can serialize/deserialize natively:

- **Primitive wrappers**: `Integer`, `Long`, `Double`, `Boolean`, `Float`, `Short`, `Byte`, `Character`
- **String**
- **POJOs**: Classes with a no-arg constructor and standard getters/setters
- **Arrays and Collections**: Of the above types

> ⚠️ **Note**: Avoid using complex generic types as keys or values due to type erasure during serialization.

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
| `computeIfAbsent(K key, Function mapper)` | Computes value if key is absent |

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

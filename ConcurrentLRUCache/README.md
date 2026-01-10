# ConcurrentLRUCache

## 📖 Overview
`ConcurrentLruCache<K,V>` is a thread-safe Least Recently Used (LRU) cache implementation in Java.  
It stores key-value pairs with a maximum capacity and automatically evicts the least recently used entries when the cache exceeds its capacity.  
The cache is designed for concurrent access, supports serialization, provides statistics, and allows safe snapshots of keys.

---

## ✨ Features
- **Thread-safe LRU cache** with automatic eviction.
- **ConcurrentHashMap** for fast key lookup.
- **Custom doubly-linked list** for LRU order (head = MRU, tail = LRU).
- **ReentrantLock** for short critical sections when updating LRU order.
- **CacheStats**: immutable DTO providing hits, misses, evictions, requests.
- **Serialization/Deserialization**: preserves cache content and LRU order; statistics reset after deserialization.
- `keysSnapshot()` returns independent copy of keys in MRU→LRU order.
- Fail-fast iterator: snapshot-based, throws `ConcurrentModificationException` if cache is modified.

---

## 🔑 Public API
```markdown
public V get(K key);
public V put(K key, V value);
public V remove(K key);
public int size();
public void clear();
public boolean containsKey(K key);
public void setMaxCapacity(int maxCapacity);
public CacheStats getStats();
public Set<K> keysSnapshot();
public Iterator<Map.Entry<K,V>> entryIterator();
```

---

## 🔒 Synchronization Strategy
- **ConcurrentHashMap** provides lock-free key lookup.
- **ReentrantLock** protects LRU list operations (insert, move, unlink, evict).
- Map operations are performed outside the lock when possible; lock is acquired only for list updates, minimizing contention.

---

## 🚫 Null Policy
- **Keys:** null keys are prohibited (throws `NullPointerException`).
- **Values:** null values are allowed.

---

## 📊 Statistics
- **hits**: successful `get` returning non-null.
- **misses**: `get` returning null.
- **requests**: total `get` calls.
- **evictions**: number of entries evicted due to capacity limits.
- Invariant: `requests = hits + misses`.
- Statistics reset after deserialization.

---

## 💾 Serialization
- **writeObject**: writes cache size and entries in MRU→LRU order.
- **readObject**: restores map and list preserving order; statistics reset.
- Lock and transient fields are not serialized.

---

## ⚡ Performance Expectations
- Tested with 50–100 threads and 100k operations.
- Invariants: `size ≤ maxCapacity`, no duplicate keys.
- Latency: acceptable under concurrent load; lock contention minimized by short critical sections.

---

## 🧪 Unit Tests
JUnit 5 tests cover:
- Basic correctness of `put/get/remove/clear`.
- Preservation of LRU order.
- Correct eviction on overflow and capacity decrease.
- Concurrent scenarios with many threads.
- Serialization/deserialization restoring content and order.
- Statistics correlation with operations.
- Fail-fast iterator behavior.

---

## 🚀 Possible Improvements
- **TTL (time-to-live):** entries expire automatically.
- **Eviction listeners:** callbacks on eviction.
- **JMX monitoring:** expose statistics via MXBean.
- **Striped locks:** reduce contention by segmenting LRU list.
- **SoftReference values:** allow GC to reclaim under memory pressure.

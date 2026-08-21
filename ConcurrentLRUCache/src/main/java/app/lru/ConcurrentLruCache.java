package app.lru;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe LRU cache combining a {@link ConcurrentHashMap} for O(1) key lookup with a
 * custom doubly-linked list (DLL) for eviction order.
 * <p>
 * <b>Lock boundaries</b>
 * <ul>
 *   <li>{@code ConcurrentHashMap.get/containsKey} — always <em>outside</em> the lock.</li>
 *   <li>{@code ReentrantLock} — held only while mutating the DLL (move-to-head, unlink,
 *       insert, evict) or when a consistent structural snapshot is required.</li>
 * </ul>
 * Statistics are updated via lock-free {@link LongAdder}s inside {@link CacheStats}.
 */
public class ConcurrentLruCache<K, V> implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Key → node index; rebuilt on deserialization. */
    private transient ConcurrentHashMap<K, Node<K, V>> map;

    /** MRU end of the DLL (most recently used). */
    private transient Node<K, V> head;

    /** LRU end of the DLL (next to evict). */
    private transient Node<K, V> tail;

    /** Guards all DLL mutations and consistent traversals. Not serializable. */
    private transient ReentrantLock lock;

    /** Maximum entries before LRU eviction. Written explicitly in {@link #writeObject}. */
    private int maxCapacity;

    /** Lock-free hit/miss/eviction counters. Reset on deserialization. */
    private transient CacheStats stats;

    /**
     * Incremented on every structural change (insert, remove, evict, clear, capacity shrink).
     * Used by {@link #entryIterator()} for fail-fast detection.
     */
    private transient long modCount;

    public ConcurrentLruCache(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be > 0");
        }
        this.maxCapacity = maxCapacity;
        initTransientState();
    }

    private void initTransientState() {
        this.map = new ConcurrentHashMap<>();
        this.head = null;
        this.tail = null;
        this.lock = new ReentrantLock();
        this.stats = new CacheStats();
        this.modCount = 0L;
    }

    private static void requireKey(Object key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public int maxCapacity() {
        return maxCapacity;
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public long hits() {
        return stats.getHits();
    }

    public long misses() {
        return stats.getMisses();
    }

    public long evictions() {
        return stats.getEvictions();
    }

    public long requests() {
        return stats.getRequests();
    }

    public CacheStats getStats() {
        return stats.snapshot();
    }

    public boolean containsKey(K key) {
        requireKey(key);
        // CHM lookup — no lock required.
        return map.containsKey(key);
    }

    /**
     * Returns the value for {@code key}, promoting it to MRU on a hit.
     * <p>
     * Lock discipline: {@code map.get} runs outside the lock; the lock is taken only
     * to re-validate the node and splice it to the head of the DLL.
     */
    public V get(K key) {
        requireKey(key);
        stats.recordRequest();

        // --- outside lock: fast CHM lookup ---
        Node<K, V> node = map.get(key);
        if (node == null) {
            stats.recordMiss();
            return null;
        }

        // --- inside lock: confirm node is still live, then promote ---
        lock.lock();
        try {
            if (map.get(key) != node) {
                // Evicted or replaced between the lock-free read and lock acquisition.
                stats.recordMiss();
                return null;
            }
            stats.recordHit();
            moveToHead(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Associates {@code key} with {@code value}. {@code put(null, value)} throws
     * {@link NullPointerException}; {@code put(key, null)} is permitted.
     */
    public V put(K key, V value) {
        requireKey(key);

        // --- outside lock: optimistic update path for existing keys ---
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            lock.lock();
            try {
                existing = map.get(key);
                if (existing != null) {
                    V old = existing.value;
                    existing.value = value;
                    moveToHead(existing);
                    modCount++;
                    return old;
                }
            } finally {
                lock.unlock();
            }
        }

        // --- inside lock: insert (with possible eviction) ---
        lock.lock();
        try {
            existing = map.get(key);
            if (existing != null) {
                V old = existing.value;
                existing.value = value;
                moveToHead(existing);
                modCount++;
                return old;
            }

            while (map.size() >= maxCapacity && tail != null) {
                evictTail();
            }

            Node<K, V> node = new Node<>(key, value);
            map.put(key, node);
            insertAtHead(node);
            modCount++;
            return null;
        } finally {
            lock.unlock();
        }
    }

    public V remove(K key) {
        requireKey(key);
        lock.lock();
        try {
            Node<K, V> node = map.remove(key);
            if (node == null) {
                return null;
            }
            unlink(node);
            modCount++;
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            map.clear();
            head = null;
            tail = null;
            modCount++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Shrinks or grows the capacity bound. When {@code newCapacity} is strictly less than
     * {@link #size()}, LRU entries are evicted immediately under the lock until
     * {@code size() == newCapacity}.
     */
    public void setMaxCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be > 0");
        }
        lock.lock();
        try {
            this.maxCapacity = newCapacity;
            while (map.size() > newCapacity && tail != null) {
                evictTail();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a consistent MRU→LRU snapshot of all keys, captured under the lock.
     */
    public Set<K> keysSnapshot() {
        LinkedHashSet<K> snapshot = new LinkedHashSet<>();
        lock.lock();
        try {
            Node<K, V> cur = head;
            while (cur != null) {
                snapshot.add(cur.key);
                cur = cur.next;
            }
        } finally {
            lock.unlock();
        }
        return snapshot;
    }

    /**
     * Returns a fail-fast iterator over MRU→LRU entries. If the cache is structurally
     * modified after this iterator is created, {@code next()} (and {@code remove()}) throw
     * {@link ConcurrentModificationException}.
     */
    public Iterator<Map.Entry<K, V>> entryIterator() {
        lock.lock();
        try {
            final long expectedModCount = this.modCount;
            final List<Map.Entry<K, V>> entries = new ArrayList<>();
            Node<K, V> cur = head;
            while (cur != null) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(cur.key, cur.value));
                cur = cur.next;
            }
            return new FailFastEntryIterator<>(entries, expectedModCount, this);
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // DLL helpers — callers must hold {@code lock}
    // -------------------------------------------------------------------------

    private void insertAtHead(Node<K, V> node) {
        node.prev = null;
        node.next = head;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    private void insertAtTail(Node<K, V> node) {
        node.next = null;
        node.prev = tail;
        if (tail != null) {
            tail.next = node;
        }
        tail = node;
        if (head == null) {
            head = node;
        }
    }

    private void moveToHead(Node<K, V> node) {
        if (node == head) {
            return;
        }
        if (node.prev != null) {
            node.prev.next = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        }
        if (node == tail) {
            tail = node.prev;
        }
        node.prev = null;
        node.next = head;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    /**
     * Detaches {@code node} from the DLL and clears its link pointers for GC safety.
     * Must be called while holding {@code lock}.
     */
    private void unlink(Node<K, V> node) {
        Node<K, V> p = node.prev;
        Node<K, V> n = node.next;
        if (p != null) {
            p.next = n;
        }
        if (n != null) {
            n.prev = p;
        }
        if (node == head) {
            head = n;
        }
        if (node == tail) {
            tail = p;
        }
        // Critical for memory safety: break the reference chain so evicted/removed nodes
        // (and transitively their values) become eligible for GC. Without nulling prev/next,
        // a stray external reference to this Node would keep the entire list segment alive.
        node.clearLinks();
    }

    private void evictTail() {
        Node<K, V> victim = tail;
        if (victim == null) {
            return;
        }
        unlink(victim);
        map.remove(victim.key);
        stats.recordEviction();
        modCount++;
    }

    // -------------------------------------------------------------------------
    // Custom serialization — captures exact MRU→LRU order
    // -------------------------------------------------------------------------

    private void writeObject(ObjectOutputStream out) throws IOException {
        lock.lock();
        try {
            out.writeInt(maxCapacity);
            out.writeInt(map.size());
            Node<K, V> cur = head;
            while (cur != null) {
                out.writeObject(cur.key);
                out.writeObject(cur.value);
                cur = cur.next;
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.maxCapacity = in.readInt();
        initTransientState();

        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            K key = (K) in.readObject();
            V value = (V) in.readObject();
            Node<K, V> node = new Node<>(key, value);
            map.put(key, node);
            // Stream is MRU-first; appending at tail preserves that order in the DLL.
            insertAtTail(node);
        }
    }

    // -------------------------------------------------------------------------
    // Fail-fast iterator
    // -------------------------------------------------------------------------

    private static final class FailFastEntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {

        private final Iterator<Map.Entry<K, V>> delegate;
        private final long expectedModCount;
        private final ConcurrentLruCache<K, V> cache;

        FailFastEntryIterator(List<Map.Entry<K, V>> entries,
                              long expectedModCount,
                              ConcurrentLruCache<K, V> cache) {
            this.delegate = entries.iterator();
            this.expectedModCount = expectedModCount;
            this.cache = cache;
        }

        private void checkConcurrentModification() {
            if (cache.modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }

        @Override
        public boolean hasNext() {
            checkConcurrentModification();
            return delegate.hasNext();
        }

        @Override
        public Map.Entry<K, V> next() {
            checkConcurrentModification();
            if (!delegate.hasNext()) {
                throw new NoSuchElementException();
            }
            return delegate.next();
        }

        @Override
        public void remove() {
            checkConcurrentModification();
            throw new UnsupportedOperationException("remove");
        }
    }
}

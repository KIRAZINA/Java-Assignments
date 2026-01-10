package app.lru;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentLruCache<K, V> implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final class Node<K, V> implements Serializable {
        private static final long serialVersionUID = 1L;
        K key;
        V value;
        transient Node<K, V> prev;
        transient Node<K, V> next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }

    private ConcurrentHashMap<K, Node<K, V>> map;
    private transient Node<K, V> head;
    private transient Node<K, V> tail;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile int maxCapacity;

    private transient LongAdder hits;
    private transient LongAdder misses;
    private transient LongAdder evictions;
    private transient LongAdder requests;

    private transient long modCount;

    public ConcurrentLruCache(int maxCapacity) {
        if (maxCapacity <= 0) throw new IllegalArgumentException("maxCapacity must be > 0");
        this.maxCapacity = maxCapacity;
        this.map = new ConcurrentHashMap<>();
        this.hits = new LongAdder();
        this.misses = new LongAdder();
        this.evictions = new LongAdder();
        this.requests = new LongAdder();
    }

    private static void requireKey(Object key) {
        if (key == null) throw new NullPointerException("key == null");
    }

    public int maxCapacity() { return maxCapacity; }
    public int size() { lock.lock(); try { return map.size(); } finally { lock.unlock(); } }
    public boolean isEmpty() { return size() == 0; }

    public long hits() { return hits.sum(); }
    public long misses() { return misses.sum(); }
    public long evictions() { return evictions.sum(); }
    public long requests() { return requests.sum(); }

    public boolean containsKey(K key) {
        requireKey(key);
        return map.containsKey(key);
    }


    public CacheStats getStats() {
        return new CacheStats(hits.sum(), misses.sum(), evictions.sum(), requests.sum()); }

    public V get(K key) {
        requireKey(key);
        requests.increment();

        Node<K,V> node = map.get(key);
        if (node == null) {
            misses.increment();
            return null;
        }

        lock.lock();
        try {
            if (map.get(key) != node) {
                return null;
            }
            hits.increment();
            moveToHead(node);
            return node.value;
        } finally { lock.unlock(); }
    }


    public V put(K key, V value) {
        requireKey(key);
        lock.lock();
        try {
            Node<K,V> existing = map.get(key);
            if (existing != null) {
                V old = existing.value;
                existing.value = value;
                moveToHead(existing);
                modCount++;
                return old;
            }

            // eviction before insert
            while (map.size() >= maxCapacity && tail != null) {
                evictTail();
            }

            Node<K,V> node = new Node<>(key, value);
            Node<K,V> prev = map.putIfAbsent(key, node);
            if (prev == null) {
                insertAtHead(node);
                modCount++;
                return null;
            } else {
                V old = prev.value;
                prev.value = value;
                moveToHead(prev);
                modCount++;
                return old;
            }
        } finally { lock.unlock(); }
    }



    private void evictTail() {
        Node<K, V> victim = tail;
        if (victim == null) return;
        unlink(victim);
        map.remove(victim.key);
        evictions.increment();
        modCount++;
    }


    public V remove(K key) {
        requireKey(key);
        lock.lock();
        try {
            Node<K, V> node = map.remove(key);
            if (node == null) return null;
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

    public void setMaxCapacity(int newMax) {
        if (newMax <= 0) throw new IllegalArgumentException("maxCapacity must be > 0");
        lock.lock();
        try {
            this.maxCapacity = newMax;
            while (map.size() > newMax && tail != null) {
                evictTail();
            }
        } finally {
            lock.unlock();
        }
    }


    public Set<K> keysSnapshot() {
        LinkedHashSet<K> snapshot = new LinkedHashSet<>();
        lock.lock();
        try {
            Node<K, V> cur = head;
            while (cur != null) { snapshot.add(cur.key); cur = cur.next; }
        } finally { lock.unlock(); }
        return snapshot;
    }

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
            return new Iterator<>() {
                private final Iterator<Map.Entry<K, V>> it = entries.iterator();
                @Override
                public boolean hasNext() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }
                    return it.hasNext();
                }
                @Override
                public Map.Entry<K, V> next() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }
                    return it.next();
                }
            };
        } finally {
            lock.unlock();
        }
    }


    private void insertAtHead(Node<K, V> node) {
        node.prev = null;
        node.next = head;
        if (head != null) head.prev = node;
        head = node;
        if (tail == null) tail = node;
    }

    private void insertAtTail(Node<K, V> node) {
        node.next = null;
        node.prev = tail;
        if (tail != null) tail.next = node;
        tail = node;
        if (head == null) head = node;
    }

    private void moveToHead(Node<K, V> node) {
        if (node == head) return;
        if (node.prev != null) node.prev.next = node.next;
        if (node.next != null) node.next.prev = node.prev;
        if (node == tail) tail = node.prev;
        node.prev = null;
        node.next = head;
        if (head != null) head.prev = node;
        head = node;
        if (tail == null) tail = node;
    }

    private void unlink(Node<K, V> node) {
        Node<K, V> p = node.prev, n = node.next;
        if (p != null) p.next = n;
        if (n != null) n.prev = p;
        if (node == head) head = n;
        if (node == tail) tail = p;
        node.prev = node.next = null;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        lock.lock();
        try {
            out.defaultWriteObject();
            out.writeInt(map.size());
            Node<K, V> cur = head;
            while (cur != null) {
                out.writeObject(cur.key);
                out.writeObject(cur.value);
                cur = cur.next;
            }
        } finally { lock.unlock(); }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.map = new ConcurrentHashMap<>();
        this.head = null;
        this.tail = null;
        this.modCount = 0L;

        this.hits = new LongAdder();
        this.misses = new LongAdder();
        this.evictions = new LongAdder();
        this.requests = new LongAdder();

        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            K key = (K) in.readObject();
            V value = (V) in.readObject();
            Node<K, V> node = new Node<>(key, value);
            map.put(key, node);
            insertAtTail(node);
        }
    }
}
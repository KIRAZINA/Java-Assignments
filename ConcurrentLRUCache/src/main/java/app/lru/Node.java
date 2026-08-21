package app.lru;

import java.io.Serializable;

/**
 * A single entry in the custom doubly-linked list (DLL) that tracks LRU order.
 * <p>
 * {@code prev} and {@code next} are {@code transient} because they are rebuilt on
 * deserialization from the serialized key/value stream; persisting pointer fields would
 * be meaningless across JVM instances.
 */
final class Node<K, V> implements Serializable {

    private static final long serialVersionUID = 1L;

    final K key;
    V value;

    /** Previous node toward the tail (less recently used). */
    transient Node<K, V> prev;

    /** Next node toward the head (more recently used). */
    transient Node<K, V> next;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Severs this node from the DLL by nulling its link pointers.
     * <p>
     * Called by {@link ConcurrentLruCache} after a node is unlinked or evicted.
     * Without clearing {@code prev}/{@code next}, an evicted node still holds strong
     * references to neighboring nodes (and transitively to their values), preventing
     * the garbage collector from reclaiming the detached subgraph. Nulling breaks
     * that reference chain so evicted entries become collectable.
     */
    void clearLinks() {
        prev = null;
        next = null;
    }
}

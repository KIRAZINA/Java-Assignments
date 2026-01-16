package com.example.collections;

public class Main {
    public static void main(String[] args) {
        // Create a new collection with initial capacity = 3
        CollectionList list = new MyCollectionList(3);

        System.out.println("Initial capacity: 3");

        // Add elements sequentially
        list.add("A");
        System.out.println("add A     → " + list);

        list.add("B");
        System.out.println("add B     → " + list);

        list.add("C");
        System.out.println("add C     → " + list);

        // Insert element at index 1
        boolean inserted = list.add(1, "X");
        System.out.println("add(1, X) → " + list + "  (success: " + inserted + ")");

        // Check if element exists
        System.out.println("contains(\"B\") → " + list.contains("B"));

        // Get element by index
        System.out.println("get(1)      → " + list.get(1));

        // Delete element by value
        boolean deleted = list.delete("X");
        System.out.println("delete(\"X\") → " + list + "  (deleted: " + deleted + ")");
        System.out.println("size()      → " + list.size());

        // Extra test: handling null values
        list.add(null);
        System.out.println("add null    → " + list);
        System.out.println("contains(null) → " + list.contains(null));
        list.delete(null);
        System.out.println("delete null → " + list);
    }
}

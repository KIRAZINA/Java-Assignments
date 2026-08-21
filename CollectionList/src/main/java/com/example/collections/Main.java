package com.example.collections;

import java.util.Iterator;

/**
 * Demonstration of MyCollectionList's features:
 *
 * <p>1. Basic add/insert/remove operations</p>
 * <p>2. Capacity management (ensureCapacity, trimToSize)</p>
 * <p>3. Fail-fast Iterator usage</p>
 * <p>4. Defensive copying</p>
 * <p>5. equals / hashCode</p>
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== MyCollectionList Demonstration ===\n");

        // -------------------------------------------------------------- //
        //  1. Basic operations                                           //
        // -------------------------------------------------------------- //
        System.out.println("1. Basic Operations");
        CollectionList list = new MyCollectionList();
        list.add("Apple");
        list.add("Banana");
        list.add("Cherry");
        list.add(1, "Blueberry");
        System.out.println("   After insertions: " + list);
        System.out.println("   Size: " + list.size());

        list.set(0, "Apricot");
        System.out.println("   After set(0, Apricot): " + list);

        list.delete("Banana");
        System.out.println("   After delete(Banana): " + list);
        System.out.println();

        // -------------------------------------------------------------- //
        //  2. Capacity management                                        //
        // -------------------------------------------------------------- //
        System.out.println("2. Capacity Management");
        MyCollectionList capList = new MyCollectionList(5);
        System.out.println("   Created with initial capacity 5");
        for (int i = 0; i < 10; i++) {
            capList.add("Item-" + i);
        }
        System.out.println("   Added 10 elements (triggers 1.5x growth: 5 -> 7 -> 10 -> 15)");
        System.out.println("   size() = " + capList.size());

        capList.trimToSize();
        System.out.println("   After trimToSize(): array shrinks to exactly match size");
        System.out.println();

        // -------------------------------------------------------------- //
        //  3. Fail-fast Iterator                                         //
        // -------------------------------------------------------------- //
        System.out.println("3. Fail-Fast Iterator");
        CollectionList itList = new MyCollectionList();
        itList.add("One");
        itList.add("Two");
        itList.add("Three");
        itList.add("Four");

        System.out.println("   Iterating with enhanced for-loop:");
        for (String s : itList) {
            System.out.println("     - " + s);
        }

        System.out.println("   Iterating with explicit Iterator.remove():");
        Iterator<String> it = itList.iterator();
        while (it.hasNext()) {
            String val = it.next();
            if (val.startsWith("T")) {
                it.remove(); // safe removal during iteration
                System.out.println("     Removed: " + val);
            }
        }
        System.out.println("   Result after removal: " + itList);
        System.out.println();

        // -------------------------------------------------------------- //
        //  4. Defensive copying                                          //
        // -------------------------------------------------------------- //
        System.out.println("4. Defensive Copying");
        String[] source = {"X", "Y", "Z"};
        MyCollectionList defList = new MyCollectionList(source);
        source[0] = "MODIFIED"; // try to corrupt the list from outside
        System.out.println("   External array corrupted, but list remains: " + defList);
        String[] exported = defList.toArray();
        System.out.print("   toArray() returns a fresh copy: [");
        for (int i = 0; i < exported.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(exported[i]);
        }
        System.out.println("]");
        System.out.println();

        // -------------------------------------------------------------- //
        //  5. Equality & Hashing                                         //
        // -------------------------------------------------------------- //
        System.out.println("5. Equality & Hashing");
        MyCollectionList eq1 = new MyCollectionList(new String[]{"A", "B", "C"});
        MyCollectionList eq2 = new MyCollectionList(new String[]{"A", "B", "C"});
        MyCollectionList eq3 = new MyCollectionList(new String[]{"A", "B", "D"});

        System.out.println("   eq1.equals(eq2): " + eq1.equals(eq2) + " (expected true)");
        System.out.println("   eq1.equals(eq3): " + eq1.equals(eq3) + " (expected false)");
        System.out.println("   eq1.hashCode() == eq2.hashCode(): " + (eq1.hashCode() == eq2.hashCode()));
        System.out.println("   eq1.equals(null): " + eq1.equals(null) + " (expected false)");
        System.out.println();

        System.out.println("=== Demonstration Complete ===");
    }
}

package app;

/**
 * Demonstrates the refined {@link DynamicStringArray}:
 *   - 2x doubling growth and capacity management
 *   - bulk addAll (minimising arraycopy calls)
 *   - null-safe search / remove and duplicate handling
 *   - compact() and removeRange()
 *   - a performance benchmark comparing one-by-one vs bulk add
 */
public class DynamicStringArrayDemo {

    public static void main(String[] args) {
        System.out.println("=== 1. 2x Growth ===");
        DynamicStringArray a = new DynamicStringArray();
        for (int i = 0; i < 10; i++) a.add("item" + i);
        System.out.println("After 10 adds: " + a);
        a.add("item10");
        System.out.println("After 11th add (2x resize to 20): " + a);

        System.out.println("\n=== 2. Bulk addAll ===");
        DynamicStringArray b = new DynamicStringArray();
        b.add("start");
        String[] bulk = {"x1", "x2", "x3", "x4"};
        b.addAll(1, bulk); // insert in the middle
        System.out.println("After addAll at index 1: " + b);
        b.addAll(new String[]{"end1", "end2"});
        System.out.println("After append addAll: " + b);

        System.out.println("\n=== 3. Null-safe search & duplicate remove ===");
        DynamicStringArray c = new DynamicStringArray();
        c.add("A");
        c.add(null);
        c.add("B");
        c.add(null);
        c.add("A");
        System.out.println("Before: " + c);
        System.out.println("indexOf(null) = " + c.indexOf(null));
        System.out.println("contains(\"B\") = " + c.contains("B"));
        c.remove("A"); // only the FIRST "A" is removed
        System.out.println("After remove(\"A\") (first only): " + c);
        c.remove(null); // removes first null
        System.out.println("After remove(null): " + c);

        System.out.println("\n=== 4. compact() ===");
        DynamicStringArray d = new DynamicStringArray();
        d.add("A");
        d.add(null);
        d.add("B");
        d.add(null);
        d.add("C");
        System.out.println("Before compact: " + d);
        int removed = d.compact();
        System.out.println("compact() removed " + removed + " nulls -> " + d);

        System.out.println("\n=== 5. removeRange(3, 7) ===");
        DynamicStringArray e = new DynamicStringArray();
        for (int i = 0; i < 10; i++) e.add("e" + i);
        System.out.println("Before: " + e);
        e.removeRange(3, 7);
        System.out.println("After removeRange(3,7): " + e);

        System.out.println("\n=== 6. Benchmark ===");
        DynamicStringArray.benchmark();
    }
}

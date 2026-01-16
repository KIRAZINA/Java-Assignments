
# Dynamic String Collection (Training Project)

## 📌 Overview
This project implements a **simple dynamic collection for `String` values** in Java.  
The goal is to practice how collections work internally: array management, resizing, element insertion, deletion, and equality checks.

Unlike Java’s built‑in `ArrayList`, this collection is implemented manually to better understand:
- How arrays grow when capacity is exceeded
- How elements are shifted during insertion and deletion
- How to handle `null` values safely
- How to compare two collections element by element

---

## 🚀 Features
- **Dynamic resizing**: the internal array grows automatically when needed (×1.5 growth factor).
- **Add elements**: at the end or at a specific index.
- **Set elements**: replace an element at a given index.
- **Delete elements**: remove by value (including `null`).
- **Contains check**: verify if an element exists.
- **Get by index**: safe retrieval with bounds checking.
- **Equals**: compare two collections by content and order.
- **Clear**: reset the collection to empty state.
- **Size**: return the current number of elements.
- **Readable output**: `toString()` prints elements in `[A, B, C]` format.

---

## 📂 Project Structure
```
src/
 └── main/
     └── java/
         └── com/example/collections/
             ├── CollectionList.java      # Interface definition
             ├── MyCollectionList.java    # Implementation
             └── Main.java                # Demo usage
 └── test/
     └── java/
         └── com/example/collections/
             └── MyCollectionListTest.java # JUnit tests
```

---

## 🛠️ Usage Example
```markdown
CollectionList list = new MyCollectionList(3);

list.add("A");
list.add("B");
list.add("C");
list.add(1, "X");

System.out.println(list); // [A, X, B, C]
System.out.println(list.contains("B")); // true
list.delete("X");
System.out.println(list); // [A, B, C]
```

---

## ✅ Testing
The project includes **JUnit 5 tests** that cover:
- Adding elements and automatic growth
- Insertion at index
- Setting and deleting elements
- Handling `null` values
- Equality checks
- Behavior on empty collection
- Negative index safety

Run tests with:
```bash
mvn test
```

---

## 🎯 Purpose
This project is a **training exercise** to deepen understanding of:
- Array manipulation
- Collection design
- Defensive programming (bounds checks, null handling)
- Writing unit tests

It is not intended as a replacement for Java’s built‑in collections, but as a learning tool to explore how they work under the hood.

# DynamicIntMatrix

A robust, low-level implementation of a dynamic integer matrix in Java. This library provides a flexible 2D array structure that can grow and shrink dynamically, supporting a wide range of matrix operations without relying on high-level Java collections (like `ArrayList`).

## 🚀 Features

-   **Dynamic Resizing**: Automatically expands capacity when adding rows/columns. Supports manual resizing with zero-padding or truncation.
-   **CRUD Operations**:
    -   Add/Insert/Remove rows and columns.
    -   Get/Set individual elements.
    -   Fill/Clear matrix.
-   **Math Operations**:
    -   Matrix Addition (`+`) and Subtraction (`-`).
    -   Scalar Multiplication (`* k`).
    -   Matrix Multiplication (`A * B`).
-   **Transformations**:
    -   Transpose.
    -   90-degree Clockwise Rotation.
    -   Submatrix extraction.
-   **I/O & Utilities**:
    -   Binary Serialization/Deserialization.
    -   Deep Copying.
    -   CSV Export.
    -   Equality checking.
-   **Performance**: Built on primitive `int[][]` arrays using `System.arraycopy` for efficient memory manipulation.

## 🛠 Requirements

-   Java 17 or higher
-   Maven 3.6+

## 📦 Installation

Clone the repository and build with Maven:

```bash
git clone https://github.com/your-username/DynamicIntMatrix.git
cd DynamicIntMatrix
mvn clean install
```

## 💻 Usage

### Basic Example

```java
import app.DynamicIntMatrix;
import app.MatrixUtils;

public class Example {
    public static void main(String[] args) {
        // Create a 2x3 matrix
        DynamicIntMatrix m = new DynamicIntMatrix();
        m.addRow(new int[]{1, 2, 3});
        m.addRow(new int[]{4, 5, 6});

        // Add a column
        m.addCol(new int[]{10, 20});
        
        // Print result
        System.out.println(m); 
        /* Output:
           DynamicIntMatrix[2x4]
           [1, 2, 3, 10]
           [4, 5, 6, 20]
        */
        
        // Math operations
        DynamicIntMatrix scaled = m.multiply(2);
    }
}
```

### Running the Demo

To see a comprehensive demonstration of all features, run the included `MatrixDemo`:

```bash
mvn compile exec:java -Dexec.mainClass="app.MatrixDemo"
```

### Running Tests

The project includes a full suite of JUnit 5 tests covering edge cases and stress tests.

```bash
mvn test
```

## 📂 Project Structure

-   `src/main/java/app/DynamicIntMatrix.java`: Core implementation.
-   `src/main/java/app/MatrixUtils.java`: Helper functions (copy, equals, csv).
-   `src/main/java/app/MatrixIO.java`: Input/Output operations.
-   `src/test/java/app/DynamicIntMatrixTest.java`: Unit tests.


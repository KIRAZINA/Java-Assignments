# In-Memory Search Engine

A lightweight in-memory search engine that indexes textual documents and supports efficient keyword-based search using an inverted index.

## Architecture

- **Document**: Model class representing a document with id and content
- **TextProcessor**: Unified text processing utility (normalize, tokenize, phrase matching)
- **InvertedIndex**: Core component implementing the inverted index data structure
- **QueryParser**: Query parsing (AND, OR, phrase)
- **SearchService**: Business logic for processing queries and ranking results
- **SearchController**: REST API endpoints

## Inverted Index

The inverted index maps each word to the set of documents containing that word:

```
word -> set of document IDs
```

Example:
```
"java" -> [doc1, doc3]
"spring" -> [doc1, doc2]
"boot" -> [doc1]
```

### Why Inverted Index is Faster

Instead of scanning all documents for each search query (O(n) where n = number of documents), the inverted index allows us to:
1. Look up each search term in the index (O(1) average using HashMap)
2. Intersect/union the document sets

**Search complexity**: O(k) where k = number of search terms

**Trade-offs**:
- **Memory**: Uses more memory as each unique word stores document references
- **Speed**: Significantly faster for search operations
- **Indexing**: Adding documents takes O(m) where m = number of words in document

## API Endpoints

### Add Document
```bash
POST /documents
Content-Type: application/json

{
  "id": "doc1",
  "content": "Java Spring Boot is powerful"
}
```

### Search (AND logic)
```bash
GET /search?q=java spring
```

### Search (OR logic)
```bash
GET /search?q=java|python
```

### Phrase Search
```bash
GET /search?q="spring boot"
```

## Example Requests/Responses

### Add documents
```bash
curl -X POST http://localhost:8080/documents \
  -H "Content-Type: application/json" \
  -d '{"id": "doc1", "content": "Java Spring Boot is powerful"}'

curl -X POST http://localhost:8080/documents \
  -H "Content-Type: application/json" \
  -d '{"id": "doc2", "content": "Python Django is great"}'

curl -X POST http://localhost:8080/documents \
  -H "Content-Type: application/json" \
  -d '{"id": "doc3", "content": "Java Spring Boot with Python"}'
```

### Search
```bash
# AND search - returns doc1 and doc3
curl "http://localhost:8080/search?q=java spring"

# OR search - returns all three documents
curl "http://localhost:8080/search?q=java|python"

# Phrase search - returns doc1 and doc3
curl "http://localhost:8080/search?q=%22spring%20boot%22"
```

## Features

- **Case-insensitive search**: All text is normalized to lowercase
- **Stop words filtering**: Common words like "the", "is", "a" are ignored during indexing and search
- **Punctuation handling**: Basic punctuation is stripped
- **OR search**: Use `|` between terms for OR logic
- **Phrase search**: Wrap phrases in quotes for exact match with word boundaries
- **Ranking**: Results sorted by number of matched keywords, with deterministic tie-breaker (descending ID)
- **Duplicate documents**: Updating existing documents removes old tokens and re-indexes
- **Thread-safe**: Uses ConcurrentHashMap for concurrent access

## Running the Application

```bash
mvn spring-boot:run
```

The application starts on port 8080 by default.

## Running Tests

```bash
mvn test
```

## Test Coverage

33 tests covering:
- Indexing (stop words, case, duplicate words, empty content)
- AND/OR search
- Phrase search with word boundaries
- Query parsing (whitespace, stop words, OR)
- Concurrency (10 threads indexing + searching)
- Text processing edge cases

## Dependencies

- Spring Boot 3.2.0
- Java 21
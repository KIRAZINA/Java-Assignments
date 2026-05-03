# Cache-First API Aggregator

A Spring Boot service that fetches data from an external API and caches results in an in-memory LRU cache with TTL.

## Features

- LRU cache with configurable maximum size and TTL
- Expiration support for write-based and access-based TTL
- Stampede protection for concurrent requests to the same key
- Cache metrics: hits, misses, evictions, hit rate
- Service layer key normalization and safe invalidation

## Structure

- `src/main/java/app/cache` - cache implementation and statistics
- `src/main/java/app/client` - external API client interface
- `src/main/java/app/config` - Spring configuration and properties
- `src/main/java/app/controller` - HTTP controller (API endpoint)
- `src/main/java/app/service` - service logic with cache coordination

## Running

1. Build the project:
   ```bash
   mvn clean package
   ```
2. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## Testing

Run all tests with:
```bash
mvn test
```

## Notes

- The cache is thread-safe and supports concurrent access.
- `DataService` normalizes query keys by trimming whitespace and converting text to lowercase.
- Invalid configuration values for cache size or TTL throw `IllegalArgumentException`.

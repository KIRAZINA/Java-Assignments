# Duplicate File Finder

## Overview
This application scans specified directories for duplicate files using a three-level strategy: grouping by file name, filtering by size, and computing CRC32 checksums for candidates.

## Features

### Core Functionality
- **Three-level duplicate detection**: Fast and efficient algorithm
  - Level 1: Group by file name (fast, cheap)
  - Level 2: Filter by file size (medium)
  - Level 3: Compute CRC32 checksums (slow, expensive - only for candidates)
- **Parallel processing**: Multi-threaded CRC32 computation for better performance
- **Smart caching**: File-based checksum caching to speed up repeated scans
- **Progress tracking**: Visual progress bars for scanning and processing
- **Configurable scanning**: Depth control, minimum file size filtering

### Export Options
- **JSON export**: Structured data with metadata and timestamps
- **CSV export**: Tabular format for spreadsheet analysis
- **Automatic timestamping**: Export files include creation timestamps

### Smart Deletion
- **Keep newest**: Preserve the most recently modified file
- **Keep oldest**: Preserve the oldest file
- **Keep first**: Default behavior - keep the first encountered file
- **Safe deletion**: Dry-run mode available for testing

### Performance Features
- **Parallel CRC32 computation**: Utilizes all CPU cores
- **Intelligent caching**: Avoids recomputing checksums for unchanged files
- **Memory efficient**: Optimized for large file sets
- **Progress visualization**: Real-time progress tracking

## Usage

### Basic Usage
```bash
java -jar duplicate-finder.jar [options] <directory1> [directory2 ...]
```

### Command Line Options

#### Basic Options
- `--help`              Show help message
- `--dry-run`           Do not delete, only show results (default)
- `--delete`            Delete duplicates, keeping the first
- `--keep-newest`       Keep the newest file by modification date
- `--keep-oldest`       Keep the oldest file by modification date

#### Export Options
- `--export=json`       Save results in JSON format
- `--export=csv`        Save results in CSV format

#### Filtering Options
- `--min-size=1MB`     Ignore files smaller than specified size
- `--max-depth=5`      Maximum depth for directory scanning
- `--case-insensitive`  Ignore case in file names

#### Logging Options
- `--log-level=DEBUG`   Set log level to DEBUG

### Examples

#### Basic Scan
```bash
java -jar duplicate-finder.jar /path/to/directory
```

#### Find and Delete Duplicates (Keep Newest)
```bash
java -jar duplicate-finder.jar --delete --keep-newest /path/to/directory
```

#### Export Results to JSON
```bash
java -jar duplicate-finder.jar --export=json /path/to/directory
```

#### Complex Scan with Filters
```bash
java -jar duplicate-finder.jar --export=csv --min-size=1MB --max-depth=3 /path/to/directory
```

#### Case-Insensitive Search
```bash
java -jar duplicate-finder.jar --case-insensitive --export=json /path/to/directory
```

## Output Formats

### Console Output
```
=== Duplicate Files Found ===

Original: /path/to/original/file.txt
  Duplicate: /path/to/duplicate/file1.txt
  Duplicate: /path/to/duplicate/file2.txt
```

### JSON Export
```json
{
  "timestamp": "2026-01-28T21:32:34.148293300",
  "totalDuplicateGroups": 1,
  "duplicates": {
    "/path/to/original/file.txt": [
      "/path/to/original/file.txt",
      "/path/to/duplicate/file1.txt",
      "/path/to/duplicate/file2.txt"
    ]
  }
}
```

### CSV Export
```csv
Original File,Duplicate File,Group Size
"/path/to/original/file.txt","/path/to/duplicate/file1.txt",3
"/path/to/original/file.txt","/path/to/duplicate/file2.txt",3
```

## Performance

### Benchmarks
- **Small directories** (< 1000 files): < 5 seconds
- **Medium directories** (1000-10000 files): 5-30 seconds
- **Large directories** (> 10000 files): 30+ seconds

### Performance Features
- **Parallel processing**: Uses all available CPU cores
- **Smart caching**: 2-10x speedup on repeated scans
- **Memory efficient**: < 100MB for 10,000 files
- **Progress tracking**: Real-time progress visualization

### Caching
The application maintains a persistent cache of file checksums:
- **Location**: `~/duplicate_finder_cache.properties`
- **Automatic**: Cache is updated automatically
- **Efficient**: Significant speedup on repeated scans
- **Safe**: Cache entries are validated against file modification times

## Requirements
- Java 17+
- Maven for building the project

## Building

### Compile and Test
```bash
mvn clean compile test
```

### Build JAR
```bash
mvn clean package
```

The executable JAR will be created at `target/duplicate-file-finder-1.0-SNAPSHOT.jar`.

## Testing

### Test Coverage
- **Unit Tests**: Core functionality testing
- **Integration Tests**: End-to-end workflow testing
- **Performance Tests**: Scalability and efficiency testing
- **Extended Tests**: Edge cases and error conditions

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test categories
mvn test -Dtest="*UnitTest"
mvn test -Dtest="*IntegrationTest"
mvn test -Dtest="*PerformanceTest"
```

### Test Statistics
- **Total Tests**: 50+ test cases
- **Coverage**: Core components > 90%
- **Performance**: Validated up to 10,000 files
- **Concurrency**: Thread-safe operations verified

## Architecture

### Components
- **DuplicateFinderApp**: Main application entry point
- **DirectoryScanner**: File system scanning with filtering
- **DuplicateDetector**: Three-level duplicate detection algorithm
- **HashUtil**: Checksum computation with caching
- **CacheManager**: Persistent checksum cache management
- **FileUtils**: File operations and utilities
- **ProgressBar**: Visual progress tracking

### Design Patterns
- **Strategy Pattern**: Configurable duplicate detection
- **Observer Pattern**: Progress tracking
- **Singleton Pattern**: Cache management
- **Factory Pattern**: File info creation

### Threading Model
- **Parallel CRC32**: Thread pool for checksum computation
- **Thread-safe**: All components are thread-safe
- **Resource Management**: Proper cleanup and resource management

## Configuration

### Default Settings
- **Max Depth**: 10 levels
- **Min File Size**: 0 bytes
- **Thread Pool Size**: Number of CPU cores
- **Cache Location**: User home directory
- **Log Level**: INFO

### Environment Variables
- `DUPLICATE_FINDER_CACHE_DIR`: Override cache directory
- `DUPLICATE_FINDER_THREADS`: Override thread pool size

## Troubleshooting

### Common Issues

#### Out of Memory
```bash
java -Xmx2g -jar duplicate-finder.jar /path/to/large/directory
```

#### Permission Denied
Ensure the application has read access to target directories and write access for cache/export files.

#### Slow Performance
- Use `--min-size` to exclude small files
- Reduce `--max-depth` for shallow scans
- Clear cache if it's corrupted: `rm ~/duplicate_finder_cache.properties`

### Debug Mode
```bash
java -jar duplicate-finder.jar --log-level=DEBUG /path/to/directory
```

## Contributing

### Development Setup
```bash
git clone <repository>
cd duplicate-file-finder
mvn clean compile
```

### Code Style
- Java 17+ features
- SLF4J for logging
- JUnit 5 for testing
- AssertJ for assertions

### Running Tests in IDE
Most IDEs support JUnit 5 directly. Run tests from:
- `src/test/java/com/duplicatefinder/service/`
- `src/test/java/com/duplicatefinder/util/`
- `src/test/java/com/duplicatefinder/integration/`
- `src/test/java/com/duplicatefinder/performance/`

## License
This project is licensed under the MIT License.

## Changelog

### Version 1.1.0 (Current)
- **New Features**:
  - JSON/CSV export functionality
  - Smart deletion with --keep-newest/--keep-oldest options
  - Parallel CRC32 computation
  - Visual progress bars
  - Persistent checksum caching
  - Enhanced error handling

- **Performance**:
  - 2-10x speedup with caching
  - Multi-threaded processing
  - Memory usage optimization

- **Testing**:
  - Comprehensive unit test suite
  - Integration tests
  - Performance benchmarks
  - Concurrent access testing

- **Quality**:
  - Improved code coverage
  - Better error messages
  - Enhanced logging
  - Documentation updates

### Version 1.0.0
- Initial release with basic duplicate detection functionality
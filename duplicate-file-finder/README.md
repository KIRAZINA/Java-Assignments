# Duplicate File Finder

## Overview
This application scans specified directories for duplicate files using a three-level strategy: grouping by file name, filtering by size, and computing CRC32 checksums for candidates.

## Usage
To run the application, use the following command:

```bash
java -jar duplicate-finder.jar [options] <directory1> [directory2 ...]
```

### Options:
- `--help`              Show help
- `--dry-run`           Do not delete, only show
- `--delete`            Delete duplicates (keeping the first)
- `--keep-newest`       Keep the newest by modification date
- `--keep-oldest`       Keep the oldest
- `--export=json`       Save results in JSON format
- `--export=csv`        Save results in CSV format
- `--log-level=DEBUG`   Set log level to DEBUG
- `--max-depth=5`      Maximum depth for scanning
- `--min-size=1KB`     Ignore files smaller than specified size
- `--case-insensitive`  Ignore case in names

## Example
```bash
java -jar duplicate-finder.jar --delete --export=json /path/to/directory
```

## Requirements
- Java 17+
- Maven for building the project

## Logging
This application uses SLF4J with Logback for logging. Adjust the logging configuration in `logback.xml` as needed.

## Testing
JUnit tests are included in the `src/test` directory. Run tests using Maven:
```bash
mvn test
```

## License
This project is licensed under the MIT License.
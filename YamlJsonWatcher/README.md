# YamlJsonWatcher

A lightweight, configurable Java 17 **daemon service** that monitors a directory for `.json`, `.yaml`, and `.yml` files and automatically converts them to the opposite format, with structured logging, input validation, and graceful error handling.

---

## Features

| Feature | Details |
|---|---|
| **Directory monitoring** | Java NIO `WatchService` — zero CPU when idle |
| **JSON → YAML** | Jackson + YAMLFactory, preserves all types |
| **YAML → JSON** | SnakeYAML + Jackson, pretty-printed output |
| **Validation** | Per-file validation with line/column error info |
| **Structured logging** | One JSON log line per conversion (timestamp, sizes, duration, status) |
| **Debouncing** | Configurable window to suppress duplicate events |
| **Configuration** | `config.yaml` + environment variable overrides |
| **Rolling logs** | Logback — daily + 10 MB cap, 30-day retention |
| **Fat JAR** | Maven Shade plugin — single runnable artefact |
| **Tests** | JUnit 5 unit + integration tests, JaCoCo coverage |

---

## Prerequisites

- Java 17+
- Maven 3.8+

---

## Build

```bash
# Full build (compile + test + fat JAR)
mvn clean package

# Skip tests (faster)
mvn clean package -DskipTests

# Tests + coverage report
mvn clean test
# Report: target/site/jacoco/index.html
```

The fat JAR is produced at `target/YamlJsonWatcher-1.0-SNAPSHOT.jar`.

---

## Run

```bash
java -jar target/YamlJsonWatcher-1.0-SNAPSHOT.jar
```

The service starts, creates the watch directory if missing, and logs:

```
=== YamlJsonWatcher starting ===
Configuration loaded: targetDir=./watch, outputDir=./watch, extensions=[.json, .yaml, .yml], debounceMs=1000, logLevel=INFO
Watching directory: /absolute/path/to/watch
```

Copy a `.json` file into the `watch/` directory — a `.yaml` file appears within ~1 second (and vice-versa).

Stop the service with **Ctrl+C** (SIGTERM); the shutdown hook ensures clean exit.

---

## Configuration

Configuration is loaded from **`config.yaml`** (bundled in the JAR) and can be overridden via environment variables.

| `config.yaml` key | Env var | Default | Description |
|---|---|---|---|
| `targetDir` | `TARGET_DIR` | `./watch` | Directory to monitor |
| `outputDir` | `OUTPUT_DIR` | _(same as targetDir)_ | Output directory for converted files |
| `extensions` | `EXTENSIONS` (comma-sep) | `.json,.yaml,.yml` | File extensions to watch |
| `debounceMs` | `DEBOUNCE_MS` | `1000` | Duplicate-event suppression window (ms) |
| `logLevel` | `LOG_LEVEL` | `INFO` | SLF4J log level |
| `logFilePath` | `LOG_FILE` | `logs/yamlJsonWatcher.log` | Rolling log file path |

### Example — override via environment variables

```bash
TARGET_DIR=/data/input OUTPUT_DIR=/data/output LOG_LEVEL=DEBUG \
  java -jar target/YamlJsonWatcher-1.0-SNAPSHOT.jar
```

### Example — custom `config.yaml`

Place a `config.yaml` **next to the JAR** (on the classpath) or mount it as a volume in Docker:

```yaml
targetDir: /data/input
outputDir: /data/output
extensions:
  - .json
  - .yaml
debounceMs: 500
logLevel: DEBUG
logFilePath: /var/log/yamlwatcher/app.log
```

> **Note:** The file inside the JAR (`src/main/resources/config.yaml`) acts as the fallback. External config files on the classpath take precedence.

---

## Log Format

Every conversion produces a single JSON log line:

```json
{"timestamp":"2026-02-28T09:12:34.123Z","source":"/watch/data.json","result":"/watch/data.yaml","inputBytes":1240,"outputBytes":987,"durationMs":15,"status":"SUCCESS","diagnostics":""}
```

Failed conversions are logged at **ERROR** level:

```json
{"timestamp":"2026-02-28T09:13:01.456Z","source":"/watch/bad.json","result":"","inputBytes":58,"outputBytes":0,"durationMs":2,"status":"FAILURE","diagnostics":"Validation failed: Invalid JSON in 'bad.json': Unexpected character at line 3, col 7"}
```

---

## Project Structure

```
YamlJsonWatcher/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/app/
│   │   │   ├── Main.java                        # Entry point
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java               # Record — all config fields
│   │   │   │   └── ConfigLoader.java            # Loads YAML + env overrides
│   │   │   ├── converter/
│   │   │   │   ├── ConversionResult.java        # Record — conversion metrics
│   │   │   │   ├── ConversionStatus.java        # Enum — SUCCESS / FAILURE
│   │   │   │   └── FileConverter.java           # JSON⟷YAML conversion
│   │   │   ├── logging/
│   │   │   │   └── ConversionLogger.java        # Structured JSON log writer
│   │   │   ├── validator/
│   │   │   │   └── FileValidator.java           # JSON / YAML validation
│   │   │   └── watcher/
│   │   │       ├── DirectoryWatcher.java        # NIO WatchService loop
│   │   │       └── FileEventHandler.java        # Validate → convert → log
│   │   └── resources/
│   │       ├── config.yaml                      # Default configuration
│   │       └── logback.xml                      # Logging configuration
│   └── test/
│       ├── java/app/
│       │   ├── config/ConfigLoaderTest.java
│       │   ├── converter/FileConverterTest.java
│       │   ├── validator/FileValidatorTest.java
│       │   └── watcher/DirectoryWatcherIntegrationTest.java
│       └── resources/
│           ├── valid.json / valid.yaml
│           ├── invalid.json / invalid.yaml
│           └── nested.json
└── target/
    └── YamlJsonWatcher-1.0-SNAPSHOT.jar        # Fat JAR (after mvn package)
```

---

## Deployment

### Linux — systemd Service

Create `/etc/systemd/system/yamljsonwatcher.service`:

```ini
[Unit]
Description=YamlJsonWatcher — JSON/YAML File Converter Daemon
After=network.target

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/yamljsonwatcher
ExecStart=/usr/bin/java -jar /opt/yamljsonwatcher/YamlJsonWatcher-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="TARGET_DIR=/data/watch"
Environment="OUTPUT_DIR=/data/output"
Environment="LOG_FILE=/var/log/yamljsonwatcher/app.log"

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable yamljsonwatcher
sudo systemctl start yamljsonwatcher
sudo systemctl status yamljsonwatcher
```

---

### Windows — NSSM (Non-Sucking Service Manager)

1. Download [NSSM](https://nssm.cc/download) and place it on your PATH.
2. Open an **Administrator** command prompt:

```bat
nssm install YamlJsonWatcher "C:\Program Files\Java\jdk-17\bin\java.exe"
nssm set  YamlJsonWatcher AppParameters "-jar C:\opt\yamlwatcher\YamlJsonWatcher-1.0-SNAPSHOT.jar"
nssm set  YamlJsonWatcher AppDirectory   "C:\opt\yamlwatcher"
nssm set  YamlJsonWatcher AppEnvironmentExtra "TARGET_DIR=C:\data\watch" "OUTPUT_DIR=C:\data\output"
nssm set  YamlJsonWatcher AppStdout      "C:\opt\yamlwatcher\logs\stdout.log"
nssm set  YamlJsonWatcher AppStderr      "C:\opt\yamlwatcher\logs\stderr.log"
nssm start YamlJsonWatcher
```

Manage the service via `services.msc` or `nssm start/stop/restart YamlJsonWatcher`.

---

### Docker

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/YamlJsonWatcher-1.0-SNAPSHOT.jar app.jar
ENV TARGET_DIR=/data/watch
ENV OUTPUT_DIR=/data/output
VOLUME ["/data/watch", "/data/output"]
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker build -t yamljsonwatcher .
docker run -d \
  -v /host/watch:/data/watch \
  -v /host/output:/data/output \
  --name yamljsonwatcher yamljsonwatcher
```

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `jackson-databind` | 2.16.1 | JSON parsing & serialisation |
| `jackson-dataformat-yaml` | 2.16.1 | YAML reading & writing |
| `snakeyaml` | 2.2 | YAML validation |
| `slf4j-api` | 2.0.11 | Logging facade |
| `logback-classic` | 1.4.14 | Logging implementation, rolling files |
| `junit-jupiter` | 5.10.1 | Unit & integration testing |
| `mockito-core` | 5.9.0 | Test mocking |
| `jacoco-maven-plugin` | 0.8.11 | Code coverage reports |
| `maven-shade-plugin` | 3.5.1 | Fat JAR packaging |

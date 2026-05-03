# Mini-Git: HTTP-Based Version Control Service

Minimal Git-like version control system accessible via HTTP. Implemented without frameworks to demonstrate HTTP protocol internals.

## Project Essence

- **No frameworks** - only `java.net.ServerSocket`
- **No HTTP servers** - raw socket implementation  
- **No JSON libraries** - manual serialization
- **Git-like storage** - content-addressable storage with SHA-1

## Project Status

### ✅ Working (68/68 tests):
- PathUtils: path management
- Sha1Hasher: SHA-1 hashing
- ObjectStore: object storage
- RefManager: reference management
- IntegrationTest: HTTP endpoints

## Quick Start

```bash
# Build
mvn clean compile

# Start server
java -cp target/classes minigit.server.HttpServer

# Usage examples
curl -X POST http://localhost:8080/init
curl -X PUT --data-binary @file.txt http://localhost:8080/objects
curl http://localhost:8080/status
```

## Architecture

```
HTTP Server → Router → Handlers → Core (ObjectStore, RefManager) → File System
```

## Endpoints

- `POST /init` - initialize repository
- `GET /status` - repository status  
- `PUT /objects` - store object
- `GET /objects/{hash}` - retrieve object
- `GET/PUT /refs/{name}` - manage references

### `POST /commit`
This is a composite **RPC-style endpoint** that:
1. Creates a commit GitObject
2. Updates `HEAD` to point to the new commit

For a pure REST workflow, use:
1. `PUT /objects` — to create the commit object manually
2. `PUT /refs/heads/main` — to advance the branch reference

## Chunked Transfer-Encoding
- **Incoming requests:** Supported (parsed manually in `RequestParser`)
- **Outgoing responses:** Not supported (all responses use `Content-Length`)

## Known Limitations
- **No atomic transactions:** Object write and ref update are separate operations. A crash between them leaves an orphan object (unreachable but harmless, identical to Git's own behavior before garbage collection).
- **Hexagonal Architecture:** Currently uses a layered architecture where handlers depend directly on the concrete `Repository` class rather than interfaces.

## Project Goal

Educational project to understand:
- How HTTP protocol works
- Content-addressable storage principles
- Git internals
- Cost of framework abstractions

---

**License:** Educational use only.

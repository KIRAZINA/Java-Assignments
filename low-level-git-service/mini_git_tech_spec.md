
# Mini-Git: HTTP-Based Version Control Service
## Technical Specification

### Overview
Build a minimal version control system accessible via raw HTTP, without using any REST frameworks (Spring, JAX-RS, etc.). The goal is to understand how REST works under the hood by implementing HTTP parsing, routing, serialization, and state management from scratch.

---

### 1. Core Philosophy
- **No REST frameworks.** No Spring Boot, no JAX-RS, no Micronaut, no Quarkus.
- **No HTTP servers.** Use only `java.net.ServerSocket` or `com.sun.net.httpserver.HttpServer` (JDK built-in) as the network layer. No Netty, no Jetty, no Tomcat.
- **No JSON libraries.** Serialize/deserialize objects manually or use `java.util.Scanner` + `StringBuilder`.
- **Minimal dependencies.** Only JUnit for testing. Everything else is hand-rolled.
- **Right-sized.** Not primitive (must handle real edge cases), but no bloat (no auth, no SSL, no distributed consensus).

---

### 2. System Architecture

```
Client (curl / custom CLI)
    │
    ▼
[HTTP Server] ──► [Request Parser] ──► [Router] ──► [Handler]
                                            │
                                            ▼
                                    [Object Store] ──► [File System]
                                            │
                                            ▼
                                    [Ref Manager] ──► [File System]
```

---

### 3. Data Model

#### 3.1 Objects (Content-Addressable Storage)
All objects are immutable and identified by SHA-1 hash of their content.

| Type | Format | Example |
|------|--------|---------|
| `blob` | `blob <size>\0<content>` | File contents |
| `tree` | `tree <size>\0<entries>` | Directory listing (mode name hash) |
| `commit` | `commit <size>\0<metadata>` | Parent, tree, author, message, timestamp |

Storage path: `.mini-git/objects/ab/cdef1234...` (first 2 chars = dir, rest = filename).

#### 3.2 References (Mutable Pointers)
Stored as plain text files in `.mini-git/refs/heads/<name>` and `.mini-git/HEAD`.

```
ref: refs/heads/main          # symbolic ref
abc123...                      # direct hash
```

---

### 4. HTTP Interface (Raw Protocol)

#### 4.1 Object Operations

| Method | Path | Body | Response | Description |
|--------|------|------|----------|-------------|
| `PUT` | `/objects` | Raw object bytes | `201 Created` + `Location: /objects/<hash>` | Store object |
| `GET` | `/objects/<hash>` | — | `200 OK` + raw bytes, or `404 Not Found` | Retrieve object |
| `HEAD` | `/objects/<hash>` | — | `200 OK` (no body), or `404` | Check existence |

#### 4.2 Reference Operations

| Method | Path | Body | Response | Description |
|--------|------|------|----------|-------------|
| `GET` | `/refs` | — | `200 OK` + list of refs | List all refs |
| `GET` | `/refs/<name>` | — | `200 OK` + hash, or `404` | Read ref |
| `PUT` | `/refs/<name>` | Hash string | `200 OK` or `201 Created` | Create/update ref |
| `DELETE` | `/refs/<name>` | — | `204 No Content` or `404` | Delete ref |

#### 4.3 HEAD

| Method | Path | Response |
|--------|------|----------|
| `GET` | `/HEAD` | Content of `.mini-git/HEAD` |
| `PUT` | `/HEAD` | Update `.mini-git/HEAD` |

#### 4.4 Repository Operations

| Method | Path | Response |
|--------|------|----------|
| `POST` | `/init` | `201 Created` — initialize `.mini-git/` directory |
| `POST` | `/clone` | `200 OK` + serialized repo state (bonus) |

---

### 5. HTTP Protocol Requirements (Hand-Rolled)

#### 5.1 Request Parsing
Must correctly parse:
- Request line: `METHOD /path HTTP/1.1`
- Headers (case-insensitive keys, multi-line values not required)
- Content-Length vs Connection: close
- Chunked transfer-encoding (bonus)

#### 5.2 Response Generation
Must generate valid responses:
- Status line with correct codes: `200`, `201`, `204`, `400`, `404`, `405`, `500`
- Content-Length or Connection: close
- Content-Type (text/plain for refs, application/octet-stream for objects)

#### 5.3 Routing
Implement a router that maps `(METHOD, path-pattern)` to handler functions. No regex libraries — use `String.startsWith()`, `String.split()`, or simple parsing.

#### 5.4 Connection Handling
- Support HTTP/1.1 keep-alive (parse Connection header)
- Handle multiple sequential requests on one connection
- Graceful socket closure

---

### 6. Core Operations to Implement

#### 6.1 `hash-object` (via HTTP)
```bash
curl -X PUT --data-binary @file.txt http://localhost:8080/objects
# Returns: 201 Created, Location: /objects/3b18e512dba79e4c8300dd08aeb37f8e728b8dad
```

#### 6.2 `cat-file` (via HTTP)
```bash
curl http://localhost:8080/objects/3b18e512dba79e4c8300dd08aeb37f8e728b8dad
```

#### 6.3 `update-ref`
```bash
curl -X PUT -d "abc123..." http://localhost:8080/refs/heads/main
```

#### 6.4 `commit` (composite operation)
Client-side workflow (or server endpoint):
1. Create tree object from staged files
2. Create commit object pointing to tree + parent commit
3. Update HEAD ref to new commit hash

**Optional server endpoint:** `POST /commit` with body containing tree hash, parent hash, message.

---

### 7. Project Structure

```
src/
├── main/java/minigit/
│   ├── server/
│   │   ├── HttpServer.java          # Entry point, socket accept loop
│   │   ├── RequestParser.java       # Raw HTTP request → Request object
│   │   ├── ResponseWriter.java      # Response object → raw HTTP bytes
│   │   ├── Router.java              # (Method, Path) → Handler
│   │   └── Handler.java             # Functional interface
│   ├── core/
│   │   ├── ObjectStore.java         # SHA-1 addressing, read/write objects
│   │   ├── RefManager.java          # Read/write refs and HEAD
│   │   ├── Repository.java          # Init, composite operations
│   │   └── ObjectType.java          # Enum: BLOB, TREE, COMMIT
│   ├── model/
│   │   ├── GitObject.java           # type + content bytes
│   │   ├── Commit.java              # parsed commit fields
│   │   ├── TreeEntry.java           # mode + name + hash
│   │   └── Ref.java                 # name + target (hash or symbolic)
│   └── util/
│       ├── Sha1Hasher.java          # SHA-1 implementation (or MessageDigest)
│       └── PathUtils.java           # Safe path joining
└── test/java/minigit/
    ├── server/
    │   ├── RequestParserTest.java
    │   ├── RouterTest.java
    │   └── IntegrationTest.java     # Full HTTP round-trips
    ├── core/
    │   ├── ObjectStoreTest.java
    │   └── RefManagerTest.java
    └── util/
        └── Sha1HasherTest.java
```

---

### 8. Constraints & Limitations

| Constraint | Rationale |
|------------|-----------|
| Single-threaded or thread-per-connection | Understand concurrency primitives; thread pool optional |
| No external JSON/XML libraries | Understand serialization trade-offs |
| No logging frameworks | Use `System.err` or hand-rolled logger |
| No build tools beyond Maven/Gradle for deps | JUnit only |
| No database | File system is the store |
| No authentication/authorization | Out of scope |
| No compression (gzip) | Out of scope |
| No SSL/TLS | Out of scope |
| Max object size: 10MB | Prevent memory issues |
| Ref names: `[a-zA-Z0-9_/-]+` | Simple validation |

---

### 9. Success Criteria

- [ ] Can initialize a repo via `POST /init`
- [ ] Can store and retrieve blob objects via raw HTTP
- [ ] Can store and retrieve tree objects
- [ ] Can create a commit chain (parent → child)
- [ ] Can read/write refs including HEAD
- [ ] Can walk commit history from a ref
- [ ] All HTTP responses are valid (tested with curl and browser dev tools)
- [ ] Unit tests cover: parsing, routing, hashing, storage
- [ ] Integration test: full commit → push → read workflow

---

### 10. Bonus Challenges

1. **Delta compression:** Store objects as diffs from base objects
2. **Packfile protocol:** `GET /objects/pack` returns multiple objects in pack format
3. **Branching & merging:** Fast-forward merge, detect merge conflicts
4. **CLI client:** Java or shell script that speaks your HTTP protocol
5. **Garbage collection:** `POST /gc` removes unreachable objects

---

### 11. Learning Goals

After completing this project, you should understand:
- How HTTP request/response parsing works byte-by-byte
- Why routing is just a `(Method, Path) → Handler` lookup table
- How content-addressable storage eliminates duplication
- Why Git is a content tracker, not a file tracker
- The cost of abstractions: what Spring Boot does for you
- Where REST ends and RPC begins

---

### 12. Deliverables

1. Source code in a single Git repository
2. `README.md` with:
   - Architecture diagram
   - Build instructions (`mvn test`, `java -jar`)
   - curl examples for every endpoint
   - Design decisions log (why you chose X over Y)
3. Test coverage report (target: >70%)
4. Optional: Blog post explaining one surprising thing you learned

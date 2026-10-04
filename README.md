# Codexonia

A web application that helps CS students understand working Java code by
generating a synchronized visual animation of its data structures, paired with
deterministic audio cues, tied to the actual execution trace.

## Repository layout

```
src/main/java/            Java backend sources (Module A: validation/execution/tracing,
                          plus the deterministic hash/tone function — ADR-2)
  HashFunction.java       FNV-1a 32-bit signature hash → deterministic tone parameters
  HashController.java     Spring endpoint exposing the hash/tone mapping
docs/                     Developer-facing docs (e.g. HashFunctionManual.md)
Project Specification/    PRD, SRS, and ADR log
Diagrams/                 UML / sequence / use-case diagrams
Study/                    Obsidian study notes (git-ignored)
test-vectors.json         Shared signature → hash → tone vectors (ADR-2 cross-language contract)
```

## Hash function quick start

```bash
javac -d out src/main/java/HashFunction.java
java -cp out HashFunction            # verify shared test vectors
java -cp out HashFunction swap       # hash + tone for one signature
```

## Runner engine quick start

```bash
javac --add-modules jdk.jdi -d out src/main/java/runner/*.java src/main/java/runner/tracer/*.java
docker build -t codexonia-sandbox:latest -f sandbox/Dockerfile .
java --add-modules jdk.jdi -cp out runner.RunnerEngine examples/Sample.java out.json.gz
java --add-modules jdk.jdi -cp out runner.SelfTest --docker   # end-to-end check
```

Compiles and runs a single `.java` file inside a hardened Docker sandbox and
emits a versioned event trace (`METHOD_CALL`, `ASSIGNMENT`, `METHOD_RETURN` in
v1). See `docs/RunnerEngineManual.md`. Validation errors, timeouts, output
caps, and runtime errors are classified, not swallowed.

See `docs/HashFunctionManual.md` for the full contract handed to the audio
engine developer.

## Web UI (Stitch pages wired up)

```bash
./run.sh
```

`run.sh` builds the sandbox image if needed, starts the Spring Boot app
(Tomcat, port 8080), and opens the Submit Code page. Flow:

1. Edit Java in the Submit page, click **Run & Visualize**.
2. The backend traces execution (`POST /api/run`) via the Docker sandbox.
3. The Visualizer & Player page replays the trace event-by-event
   (play/pause, step, scrub, speed) with code highlighting, call stack,
   and stdout.

`GET /hash?input=...` exposes the deterministic hash → tone mapping.

## Docker

```bash
docker build -t codexonia .
docker run --rm codexonia
```

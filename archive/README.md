# Archived Files

This directory documents files that have been removed from the active project
but may be restored later. Compressed archives (`.zip`) are intentionally
ignored by Git via `.gitignore`; the documentation below is tracked so an
archive can be reconstructed from source even if the local zip is lost.

## Root `Dockerfile` — hash-vector verification image

| Field | Value |
| --- | --- |
| Original path | `Dockerfile` (repository root) |
| Archived on | 2026-11-01 |
| Archive | `archive/root-Dockerfile.zip` |
| SHA-256 of original | `872b68232cb7642e5379f246a12adc5b44662db7f71e7890f139ae9f0a2ad2a6` |
| Status | Not used by `run.sh`, the Spring Boot app, or the sandbox runner |

### What it was

This was a minimal Docker image for verifying the FNV-1a `HashFunction` /
tone test vectors. It was **not** a deployable web-app image: it did not contain
Maven, Spring Boot, the runner, the visualizer, or the static site.

It started from `eclipse-temurin:26-jdk`, copied `src/` and `test-vectors.json`,
compiled only `src/main/java/HashFunction.java`, and ran `HashFunction` as its
default command.

The actual user-code execution image remains at `sandbox/Dockerfile`, built as
`codexonia-sandbox:latest`.

### Original contents

```dockerfile
# Codexonia backend image
# Base: official Eclipse Temurin JDK 26 (matches your local JDK 26.0.2)
FROM eclipse-temurin:26-jdk

# Working directory inside the container
WORKDIR /app

# Copy only what the build needs (see .dockerignore)
COPY src/ src/
COPY test-vectors.json .

# Compile the Java sources the same way you do locally
RUN javac -d out src/main/java/HashFunction.java

# Default command: verify the shared test vectors
CMD ["java", "-cp", "out", "HashFunction"]
```

### Original usage

```bash
docker build -t codexonia .
docker run --rm codexonia
```

### Restore

From the repository root:

```bash
unzip archive/root-Dockerfile.zip -d .
```

The zip contains `Dockerfile` at its root, so the command above restores it to
the repository root. If the zip is missing, recreate `Dockerfile` from the
source block above.

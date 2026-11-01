# syntax=docker/dockerfile:1
# Codexonia job sandbox image (ADR-7 subset).
# Hardening (no network, read-only rootfs, dropped caps, non-root, rlimits)
# is applied by the `docker run` flags in DockerSandbox.java.

FROM eclipse-temurin:26-jdk AS build

WORKDIR /build
COPY src/main/java/runner/Json.java src/main/java/runner/TraceEvent.java \
     src/main/java/runner/EventType.java ./src/main/java/runner/
COPY src/main/java/runner/tracer/ ./src/main/java/runner/tracer/

# Compile the tracer and its small, framework-free runner dependencies.
RUN mkdir -p out \
    && javac --add-modules jdk.jdi -d out \
       src/main/java/runner/*.java src/main/java/runner/tracer/*.java

FROM eclipse-temurin:26-jdk

LABEL org.opencontainers.image.title="Codexonia job sandbox" \
      org.opencontainers.image.description="Hardened per-job Java tracing sandbox"

COPY --from=build /build/out/ /opt/codexonia/

RUN useradd -m tracer
WORKDIR /workspace
USER tracer

ENTRYPOINT ["java", "--add-modules", "jdk.jdi", "-cp", "/opt/codexonia", "runner.tracer.TracerMain"]

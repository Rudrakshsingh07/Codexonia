# Codexonia job sandbox image (ADR-7 subset).
# Hardening (no network, read-only rootfs, dropped caps, non-root, rlimits)
# is applied by the `docker run` flags in DockerSandbox.java.
FROM eclipse-temurin:26-jdk

# Precompiled runner tracer classes (javac -d out ... on the host first).
COPY out/runner/ /opt/codexonia/runner/

RUN useradd -m tracer
WORKDIR /workspace
USER tracer

ENTRYPOINT ["java", "--add-modules", "jdk.jdi", "-cp", "/opt/codexonia", "runner.tracer.TracerMain"]

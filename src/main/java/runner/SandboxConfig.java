package runner;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runtime configuration shared by {@link RunnerEngine} and {@link DockerSandbox}.
 *
 * <p>When the web app runs on the host, job workspaces live under the system temp
 * directory and are bind-mounted into the sandbox container. When the app runs in
 * Docker, {@code CODEXONIA_JOBS_DIR} points at a named volume that is also visible
 * to the host Docker daemon, and {@code CODEXONIA_DOCKER_VOLUME} tells the runner
 * to mount just the job's subdirectory of that volume. The named-volume path is
 * what keeps the sandbox working on Docker Desktop for Windows and macOS, where a
 * container-local path cannot be bind-mounted by the host daemon.
 */
final class SandboxConfig {

    private SandboxConfig() {
    }

    static Path jobsRoot() {
        String configured = envOrProperty("CODEXONIA_JOBS_DIR", "codexonia.jobsDir");
        if (configured == null || configured.isBlank()) {
            return Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize();
    }

    static String dockerVolume() {
        String configured = envOrProperty("CODEXONIA_DOCKER_VOLUME", "codexonia.dockerVolume");
        return configured == null || configured.isBlank() ? null : configured.trim();
    }

    private static String envOrProperty(String env, String property) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) {
            value = System.getProperty(property);
        }
        return value;
    }
}

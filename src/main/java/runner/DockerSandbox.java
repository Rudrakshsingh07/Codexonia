package runner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Starts a hardened Docker container per job and tears it down (or kills it on timeout). */
public final class DockerSandbox {

    private static final String CONTAINER_WORKSPACE = "/workspace";

    public static final class SandboxRun {
        public final String status;
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final String errorMessage;

        SandboxRun(String status, int exitCode, String stdout, String stderr, String errorMessage) {
            this.status = status;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.errorMessage = errorMessage;
        }

        public boolean completed() {
            return "COMPLETE".equals(status);
        }
    }

    private final LimitPolicy policy;
    private final Path jobsRoot;
    private final String dockerVolume;

    public DockerSandbox(LimitPolicy policy) {
        this(policy, SandboxConfig.jobsRoot(), SandboxConfig.dockerVolume());
    }

    DockerSandbox(LimitPolicy policy, Path jobsRoot, String dockerVolume) {
        this.policy = policy;
        this.jobsRoot = jobsRoot.toAbsolutePath().normalize();
        String volume = dockerVolume == null ? null : dockerVolume.trim();
        this.dockerVolume = volume == null || volume.isEmpty() ? null : volume;
    }

    public boolean imagePresent() {
        try {
            Process p = new ProcessBuilder("docker", "image", "inspect", policy.image)
                    .redirectErrorStream(true).start();
            p.waitFor(10, TimeUnit.SECONDS);
            drain(p.getInputStream());
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public SandboxRun run(Path workspace, String mainClass) throws IOException, InterruptedException {
        if (!imagePresent()) {
            return new SandboxRun("SANDBOX_ERROR", -1, "", "",
                    "Sandbox image '" + policy.image + "' not found.\n"
                            + "Start the containerized stack with:\n"
                            + "  docker compose up --build\n"
                            + "or build the sandbox image from the repo root:\n"
                            + "  docker build -t " + policy.image + " -f sandbox/Dockerfile .");
        }
        String mountProblem = mountProblem(workspace);
        if (mountProblem != null) {
            return new SandboxRun("SANDBOX_ERROR", -1, "", "", mountProblem);
        }
        String name = "codexonia-job-" + UUID.randomUUID();
        List<String> cmd = dockerCommand(workspace, name, mainClass);

        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream dockerOut = new ByteArrayOutputStream();
        Thread drainer = new Thread(() -> {
            byte[] tmp = new byte[4096];
            try {
                InputStream in = process.getInputStream();
                int n;
                while ((n = in.read(tmp)) != -1) {
                    synchronized (dockerOut) {
                        if (dockerOut.size() < 64 * 1024) {
                            dockerOut.write(tmp, 0, Math.min(n, 64 * 1024 - dockerOut.size()));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }, "docker-drain");
        drainer.setDaemon(true);
        drainer.start();

        boolean finished = process.waitFor((long) policy.wallTimeoutSeconds + policy.killGraceSeconds + 5, TimeUnit.SECONDS);
        if (!finished) {
            try {
                new ProcessBuilder("docker", "kill", name).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return new SandboxRun("TIMEOUT", -1, dockerOut.toString(StandardCharsets.UTF_8), "",
                    "Wall-clock limit of " + policy.wallTimeoutSeconds + "s exceeded; container killed");
        }
        int code = process.exitValue();
        drainer.join(1000);

        Path resultPath = workspace.resolve("run-result.json");
        if (!Files.exists(resultPath)) {
            return new SandboxRun("INTERNAL_ERROR", code, dockerOut.toString(StandardCharsets.UTF_8), "",
                    "run-result.json not produced by the sandbox. docker output:\n"
                            + tail(dockerOut.toString(StandardCharsets.UTF_8), 1024));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) Json.parse(Files.readString(resultPath));
            return new SandboxRun(
                    (String) m.get("status"),
                    ((Number) m.get("exit_code")).intValue(),
                    (String) m.get("stdout"),
                    (String) m.get("stderr"),
                    (String) m.get("error_message"));
        } catch (Exception e) {
            return new SandboxRun("INTERNAL_ERROR", code, dockerOut.toString(StandardCharsets.UTF_8), "",
                    "Malformed run-result.json: " + e.getMessage());
        }
    }

    /**
     * Builds the hardened {@code docker run} invocation. Kept package-private so tests
     * can verify both the mount strategy and the security flags without Docker.
     */
    List<String> dockerCommand(Path workspace, String name, String mainClass) {
        Path ws = workspace.toAbsolutePath().normalize();
        List<String> cmd = new ArrayList<>(Arrays.asList(
                "docker", "run", "--rm", "--name", name,
                "--network", "none",
                "--memory", policy.memoryMb + "m",
                "--cpus", String.valueOf(policy.cpus),
                "--pids-limit", String.valueOf(policy.maxProcesses),
                "--read-only",
                "--tmpfs", "/tmp:rw,size=" + policy.tmpfsMb + "m",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--init",
                "-w", CONTAINER_WORKSPACE));
        if (dockerVolume != null) {
            cmd.add("--mount");
            cmd.add("type=volume,source=" + dockerVolume
                    + ",target=" + CONTAINER_WORKSPACE
                    + ",volume-subpath=" + volumeSubpath(ws));
        } else {
            cmd.add("-v");
            cmd.add(ws + ":" + CONTAINER_WORKSPACE + ":Z");
        }
        cmd.add(policy.image);
        cmd.add(CONTAINER_WORKSPACE + "/src");
        cmd.add(CONTAINER_WORKSPACE + "/classes");
        cmd.add(CONTAINER_WORKSPACE + "/trace.jsonl");
        cmd.add(CONTAINER_WORKSPACE + "/run-result.json");
        cmd.add(mainClass);
        cmd.add(String.valueOf(policy.maxEvents));
        cmd.add(String.valueOf(policy.wallTimeoutSeconds));
        return cmd;
    }

    String mountProblem(Path workspace) {
        if (dockerVolume == null) {
            return null;
        }
        Path ws = workspace.toAbsolutePath().normalize();
        if (ws.equals(jobsRoot) || !ws.startsWith(jobsRoot)) {
            return "Job workspace " + ws + " is outside CODEXONIA_JOBS_DIR " + jobsRoot
                    + "; it cannot be exposed through Docker volume '" + dockerVolume + "'.";
        }
        if (jobsRoot.relativize(ws).getNameCount() != 1) {
            return "Job workspace " + ws + " must be a direct child of CODEXONIA_JOBS_DIR "
                    + jobsRoot + " when CODEXONIA_DOCKER_VOLUME is set.";
        }
        return null;
    }

    private String volumeSubpath(Path workspace) {
        return jobsRoot.relativize(workspace).toString().replace(File.separatorChar, '/');
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    private static void drain(InputStream in) {
        try {
            byte[] tmp = new byte[4096];
            while (in.read(tmp) != -1) {
                // discard
            }
        } catch (IOException ignored) {
        }
    }
}

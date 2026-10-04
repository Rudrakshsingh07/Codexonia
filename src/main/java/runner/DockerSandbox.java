package runner;

import java.io.ByteArrayOutputStream;
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

    public DockerSandbox(LimitPolicy policy) {
        this.policy = policy;
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
                            + "Build it from the repo root:\n"
                            + "  docker build -t " + policy.image + " -f sandbox/Dockerfile .");
        }
        String name = "codexonia-job-" + UUID.randomUUID();
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
                "-w", "/workspace",
                "-v", workspace.toAbsolutePath() + ":/workspace:Z"));
        cmd.add(policy.image);
        cmd.add("/workspace/src");
        cmd.add("/workspace/classes");
        cmd.add("/workspace/trace.jsonl");
        cmd.add("/workspace/run-result.json");
        cmd.add(mainClass);
        cmd.add(String.valueOf(policy.maxEvents));
        cmd.add(String.valueOf(policy.wallTimeoutSeconds));

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

package runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Facade for the runner engine.
 *
 * run(): validate submission sizes -> detect main class -> write workspace ->
 *        DockerSandbox.run() -> parse trace.jsonl -> Trace JSON (+gzip).
 *
 * The workspace is always deleted afterwards; the returned trace is self-contained.
 */
public final class RunnerEngine {

    private final DockerSandbox sandbox;
    private final LimitPolicy policy;

    public RunnerEngine() {
        this(LimitPolicy.defaults());
    }

    public RunnerEngine(LimitPolicy policy) {
        this.policy = policy;
        this.sandbox = new DockerSandbox(policy);
    }

    public RunOutcome run(CodeSubmission submission) {
        if (submission.files.size() > policy.maxFiles) {
            return RunOutcome.limit("Too many files: " + submission.files.size() + " (max " + policy.maxFiles + ")");
        }
        if (submission.totalSourceBytes() > policy.maxSourceBytes) {
            return RunOutcome.limit("Total source exceeds " + policy.maxSourceBytes + " bytes");
        }
        String mainClass = MainClassDetector.detect(submission);
        if (mainClass == null) {
            return RunOutcome.error("NO_MAIN_METHOD", "No 'public static void main(String[])' found in any submitted file");
        }

        Path ws;
        try {
            ws = Files.createTempDirectory("codexonia-job-");
        } catch (IOException e) {
            return RunOutcome.error("INTERNAL_ERROR", e.getMessage());
        }
        try {
            Path src = ws.resolve("src");
            Files.createDirectories(src);
            for (JavaSourceFile f : submission.files) {
                Files.write(src.resolve(f.fileName), f.source.getBytes(StandardCharsets.UTF_8));
            }
            Files.createDirectories(ws.resolve("classes"));
            relaxPermissions(ws);

            DockerSandbox.SandboxRun run = sandbox.run(ws, mainClass);

            List<TraceEvent> events = new ArrayList<>();
            Path tracePath = ws.resolve("trace.jsonl");
            if (Files.exists(tracePath)) {
                for (String line : Files.readAllLines(tracePath, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) Json.parse(line);
                            events.add(TraceEvent.fromJson(m));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            String traceJson = TraceSerializer.toJson(submission, events, run.stdout, run.status);
            byte[] traceGz = TraceSerializer.gzip(traceJson);
            if (run.completed()) {
                return RunOutcome.ok(traceJson, traceGz, events.size());
            }
            return RunOutcome.error(run.status, run.errorMessage, traceJson, traceGz, events.size());
        } catch (Exception e) {
            return RunOutcome.error("INTERNAL_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            deleteRecursive(ws);
        }
    }

    private static void relaxPermissions(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            s.forEach(p -> {
                try {
                    Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(
                            Files.isDirectory(p) ? "rwxrwxrwx" : "rw-rw-rw-"));
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void deleteRecursive(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            System.err.println("Usage: java -cp out runner.RunnerEngine <file.java> [trace.json.gz]");
            System.exit(2);
        }
        Path file = Paths.get(args[0]);
        String source = Files.readString(file, StandardCharsets.UTF_8);
        RunnerEngine engine = new RunnerEngine();
        RunOutcome result = engine.run(CodeSubmission.singleFile(file.getFileName().toString(), source));
        System.out.println("status: " + result.status + "  events: " + result.eventCount);
        if (result.errorMessage != null) {
            System.out.println("error: " + result.errorMessage);
        }
        if (args.length > 1 && result.traceGz != null && result.traceGz.length > 0) {
            Files.write(Paths.get(args[1]), result.traceGz);
            System.out.println("trace written to " + args[1]);
        } else if (result.traceJson != null && !result.traceJson.isEmpty()) {
            System.out.println(result.traceJson);
        }
        System.exit(result.ok ? 0 : 1);
    }
}

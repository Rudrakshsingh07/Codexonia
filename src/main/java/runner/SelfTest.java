package runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Dependency-free checks; run with `--docker` to exercise the real sandbox path. */
public final class SelfTest {

    public static void main(String[] a) throws Exception {
        // 1. JSON string round-trip
        String tricky = "q\"b\\c\nd\te";
        Object parsed = Json.parse(Json.escape(tricky));
        check(tricky.equals(parsed), "json string round-trip");

        // 2. TraceEvent round-trip (assignment + escaping through args list not used here)
        TraceEvent e = TraceEvent.assignment(3, "Sample.java", "Sample", "main", 7, "x", "1", "2");
        @SuppressWarnings("unchecked")
        TraceEvent e2 = TraceEvent.fromJson((Map<String, Object>) Json.parse(e.toJson()));
        check(e2.seq == 3 && e2.type == EventType.ASSIGNMENT && "x".equals(e2.variable)
                && "1".equals(e2.oldValue) && "2".equals(e2.newValue), "TraceEvent assignment round-trip");

        TraceEvent c = TraceEvent.call(1, "A.java", "A", "main", 3, List.of("\"hi\"", "[1, 2]"));
        @SuppressWarnings("unchecked")
        TraceEvent c2 = TraceEvent.fromJson((Map<String, Object>) Json.parse(c.toJson()));
        check(c2.arguments.size() == 2 && "\"hi\"".equals(c2.arguments.get(0)), "TraceEvent call args round-trip");

        // 3. MainClassDetector
        String src = "package ex;\npublic class Foo {\n  public static void main(String[] args) { }\n}\n";
        check("ex.Foo".equals(MainClassDetector.detect(CodeSubmission.singleFile("Foo.java", src))),
                "MainClassDetector with package");
        String src2 = "public class Bar {\n  public static void main(String... a) { }\n}\n";
        check("Bar".equals(MainClassDetector.detect(CodeSubmission.singleFile("Bar.java", src2))),
                "MainClassDetector varargs main");
        check(MainClassDetector.detect(CodeSubmission.singleFile("Nope.java", "class Nope { }")) == null,
                "MainClassDetector negative");

        // 4. TraceSerializer shape
        String json = TraceSerializer.toJson(CodeSubmission.singleFile("Sample.java", ""),
                List.of(e), "hi", "COMPLETE");
        check(json.contains("\"schema_version\":1") && json.contains("\"events\":["), "TraceSerializer shape");

        // 5. Real sandbox path (needs Docker + built image)
        if (a.length > 0 && a[0].equals("--docker")) {
            String sample = Files.readString(Path.of("examples/Sample.java"), StandardCharsets.UTF_8);
            RunOutcome r = new RunnerEngine().run(CodeSubmission.singleFile("Sample.java", sample));
            System.out.println("docker run -> status=" + r.status + " events=" + r.eventCount
                    + (r.errorMessage != null ? " msg=" + r.errorMessage : ""));
            check(r.ok, "docker sandbox run completes");
            check(r.traceJson.contains("\"type\":\"METHOD_CALL\""), "trace has METHOD_CALL");
            check(r.traceJson.contains("\"type\":\"ASSIGNMENT\""), "trace has ASSIGNMENT");
        }

        System.out.println("SELFTEST OK");
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            System.err.println("FAIL: " + label);
            System.exit(1);
        }
        System.out.println("ok: " + label);
    }
}

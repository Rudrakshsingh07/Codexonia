import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import runner.CodeSubmission;
import runner.JavaSourceFile;
import runner.RunnerEngine;
import runner.RunOutcome;
import runner.TraceSerializer;
import visualizer.TimelineBuilder;
import visualizer.ToneProvider;
import visualizer.ToneSpec;

@RestController
public class RunController {

    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("public\\s+class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern MAIN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(");

    /**
     * The shared HashFunction is in the default package; inject its result into the
     * framework-free visualizer core rather than duplicating the mapping there.
     */
    private static final ToneProvider TONES = signature -> {
        HashFunction.ToneParameters tone = HashFunction.toneFor(signature);
        return new ToneSpec(signature, tone.frequencyHz, tone.oscillator, tone.durationMs,
                tone.noteIndex, tone.midiNote, HashFunction.hashHex(signature));
    };

    public record FileDto(String fileName, String source, boolean isMainFile) {}
    public record RunRequest(String source, List<FileDto> files) {}

    @PostMapping(value = "/api/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> run(@RequestBody RunRequest req) {
        CodeSubmission submission;
        if (req != null && req.files() != null && !req.files().isEmpty()) {
            JavaSourceFile[] arr = new JavaSourceFile[req.files().size()];
            for (int i = 0; i < arr.length; i++) {
                FileDto f = req.files().get(i);
                boolean main = f.isMainFile() || MAIN.matcher(f.source()).find();
                arr[i] = new JavaSourceFile(f.fileName(), f.source(), main);
            }
            submission = CodeSubmission.of(arr);
        } else if (req != null && req.source() != null && !req.source().isBlank()) {
            String source = req.source();
            Matcher m = PUBLIC_CLASS.matcher(source);
            String className = m.find() ? m.group(1) : "Main";
            submission = CodeSubmission.singleFile(className + ".java", source);
        } else {
            return ResponseEntity.badRequest()
                    .body("{\"status\":\"NO_SOURCE\",\"error_message\":\"No source provided\"}");
        }
        RunOutcome outcome = new RunnerEngine().run(submission);
        if (outcome.traceJson != null && !outcome.traceJson.isBlank()) {
            String timeline = TimelineBuilder.build(submission, outcome.traceJson, TONES);
            return ResponseEntity.ok(TimelineBuilder.attach(outcome.traceJson, timeline));
        }
        // Early validation failures (no main, limits, internal errors) still get a
        // trace-shaped response so the report and timeline sources stay consistent.
        String traceJson = TraceSerializer.toJson(submission, List.of(), "", outcome.status,
                outcome.errorMessage);
        String timeline = TimelineBuilder.build(submission, traceJson, TONES);
        return ResponseEntity.ok(TimelineBuilder.attach(traceJson, timeline));
    }
}

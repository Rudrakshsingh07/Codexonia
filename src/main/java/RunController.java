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

@RestController
public class RunController {

    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("public\\s+class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern MAIN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(");

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
        if (outcome.traceJson != null) {
            return ResponseEntity.ok(outcome.traceJson);
        }
        return ResponseEntity.ok("{\"schema_version\":1,\"status\":\"" + outcome.status
                + "\",\"error_message\":" + jsonQuote(outcome.errorMessage)
                + ",\"event_count\":0,\"events\":[]}");
    }

    private static String jsonQuote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}

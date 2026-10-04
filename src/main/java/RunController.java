import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import runner.CodeSubmission;
import runner.RunnerEngine;
import runner.RunOutcome;

@RestController
public class RunController {

    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("public\\s+class\\s+([A-Za-z_$][\\w$]*)");

    public record RunRequest(String source) {}

    @PostMapping(value = "/api/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> run(@RequestBody RunRequest req) {
        if (req == null || req.source() == null || req.source().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("{\"status\":\"NO_SOURCE\",\"error_message\":\"No source provided\"}");
        }
        String source = req.source();
        Matcher m = PUBLIC_CLASS.matcher(source);
        String className = m.find() ? m.group(1) : "Main";
        CodeSubmission submission = CodeSubmission.singleFile(className + ".java", source);
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

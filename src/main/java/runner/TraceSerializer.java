package runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** Serializes a TraceEvent stream into the versioned Trace JSON from ADR-8 (v1 schema). */
public final class TraceSerializer {

    private TraceSerializer() {}

    public static String toJson(CodeSubmission submission, List<TraceEvent> events, String stdout, String status) {
        StringBuilder sb = new StringBuilder("{\"schema_version\":1");
        sb.append(",\"status\":").append(Json.escape(status));
        sb.append(",\"sources\":[");
        for (int i = 0; i < submission.files.size(); i++) {
            JavaSourceFile f = submission.files.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"file_name\":").append(Json.escape(f.fileName))
                    .append(",\"is_main_file\":").append(f.isMainFile).append('}');
        }
        sb.append("],\"stdout\":").append(stdout == null ? "null" : Json.escape(stdout));
        sb.append(",\"event_count\":").append(events.size());
        sb.append(",\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(events.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    public static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (OutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }
}

package visualizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import runner.CodeSubmission;
import runner.EventType;
import runner.JavaSourceFile;
import runner.Json;
import runner.TraceEvent;

/**
 * Turns the ordered trace into a precomputed {@code VisualizationTimeline}.
 *
 * <p>This is the Java half of the visualizer contract: call-stack replay, variable
 * state, array extraction, labels, layout geometry, and tone specs are all decided
 * here. The browser is only allowed to paint a frame.</p>
 */
public final class TimelineBuilder {

    public static final int TIMELINE_SCHEMA_VERSION = 1;
    public static final int TRACE_SCHEMA_VERSION = 1;

    /** Bounds the DOM/player work for a single run; the trace itself stays available. */
    public static final int MAX_FRAMES = 8000;
    public static final int MAX_JSON_CHARS = 4_000_000;

    private static final int ARRAY_CELL_WIDTH = 48;
    private static final int ARRAY_CELL_HEIGHT = 40;
    private static final int ARRAY_CELL_STEP = 56;

    private TimelineBuilder() {}

    public static String build(CodeSubmission submission, String traceJson, ToneProvider tones) {
        String status = "UNKNOWN";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> trace = (Map<String, Object>) Json.parse(traceJson);
            status = string(trace.get("status"), "UNKNOWN");
            String errorMessage = trace.get("error_message") == null ? null
                    : String.valueOf(trace.get("error_message"));
            String stdout = trace.get("stdout") == null ? "" : String.valueOf(trace.get("stdout"));
            List<TraceEvent> events = parseEvents(trace);
            if (events.size() > MAX_FRAMES) {
                return error(submission, status, "TIMELINE_TOO_LARGE",
                        "Timeline has " + events.size() + " frames (max " + MAX_FRAMES
                                + "). The raw trace is still available.");
            }
            List<SceneState> frames = buildFrames(events, stdout, tones);
            String json = serialize(submission, status, errorMessage, frames);
            if (json.length() > MAX_JSON_CHARS) {
                return error(submission, status, "TIMELINE_TOO_LARGE",
                        "Timeline is larger than " + MAX_JSON_CHARS
                                + " characters. The raw trace is still available.");
            }
            return json;
        } catch (Exception e) {
            return error(submission, status, "TIMELINE_ERROR",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Add the timeline as an additive sibling of the frozen trace fields. */
    public static String attach(String traceJson, String timelineJson) {
        if (traceJson == null || traceJson.isBlank() || timelineJson == null || timelineJson.isBlank()) {
            return traceJson;
        }
        int end = traceJson.lastIndexOf('}');
        if (end < 0) {
            return traceJson;
        }
        return traceJson.substring(0, end) + ",\"timeline\":" + timelineJson + "}";
    }

    private static List<TraceEvent> parseEvents(Map<String, Object> trace) {
        Object raw = trace.get("events");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<TraceEvent> events = new ArrayList<>(list.size());
        for (Object item : list) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            events.add(TraceEvent.fromJson(map));
        }
        return events;
    }

    private static List<SceneState> buildFrames(List<TraceEvent> events, String stdout, ToneProvider tones) {
        List<SceneState> frames = new ArrayList<>(events.size());
        Deque<FrameState> stack = new ArrayDeque<>();

        for (int i = 0; i < events.size(); i++) {
            TraceEvent event = events.get(i);
            FrameState active;

            switch (event.type) {
                case METHOD_CALL -> {
                    active = new FrameState(event);
                    stack.push(active);
                }
                case METHOD_RETURN -> {
                    active = findFrame(stack, event);
                    if (active != null) {
                        active.line = event.line;
                    }
                }
                case ASSIGNMENT -> {
                    active = findFrame(stack, event);
                    if (active != null) {
                        active.line = event.line;
                        if (event.variable != null) {
                            active.variables.put(event.variable, event.newValue);
                        }
                    }
                }
                default -> {
                    active = findFrame(stack, event);
                    if (active != null) {
                        active.line = event.line;
                    }
                }
            }

            if (active == null) {
                // A malformed/partial trace should still render; synthesize the frame
                // from the event instead of dropping the rest of the timeline.
                active = new FrameState(event);
                stack.push(active);
            }

            SceneState scene = snapshot(event, active, stack, i == events.size() - 1,
                    stdout, frames.size(), tones);
            frames.add(scene);

            if (event.type == EventType.METHOD_RETURN) {
                stack.remove(active);
            }
        }
        return frames;
    }

    private static SceneState snapshot(TraceEvent event, FrameState active, Deque<FrameState> stack,
                                       boolean isLastFrame, String stdout, int index, ToneProvider tones) {
        SceneState scene = new SceneState();
        scene.index = index;
        scene.seq = event.seq;
        scene.eventJson = event.toJson();
        scene.eventType = event.type.name();
        scene.eventSignature = signatureFor(event.type);
        scene.activeFile = event.file != null ? event.file
                : active.file != null ? active.file : activeFile(stack);
        scene.activeLine = event.line > 0 ? event.line : active.line;
        scene.labelTitle = labelFor(event);
        scene.labelSubtitle = subtitleFor(scene.activeFile, scene.activeLine);
        scene.stdoutVisible = isLastFrame && stdout != null ? stdout : "";

        List<FrameState> ordered = new ArrayList<>(stack);
        Collections.reverse(ordered);
        for (int i = 0; i < ordered.size(); i++) {
            FrameState frame = ordered.get(i);
            SceneState.CallFrame call = new SceneState.CallFrame();
            call.className = frame.className;
            call.methodName = frame.methodName;
            call.file = frame.file;
            call.line = frame.line;
            call.depth = i;
            call.arguments = frame.arguments;
            scene.callStack.add(call);
        }

        for (Map.Entry<String, String> entry : active.variables.entrySet()) {
            SceneState.Variable variable = new SceneState.Variable();
            variable.name = entry.getKey();
            variable.value = entry.getValue();
            variable.changed = event.type == EventType.ASSIGNMENT
                    && entry.getKey().equals(event.variable);
            if (variable.changed) {
                variable.oldValue = event.oldValue;
                variable.newValue = event.newValue;
            }
            scene.variables.add(variable);
        }

        for (SceneState.Variable variable : scene.variables) {
            List<String> elements = parseArray(variable.value);
            if (elements == null || elements.isEmpty()) {
                continue;
            }
            SceneState.Structure structure = new SceneState.Structure();
            structure.name = variable.name;
            structure.kind = "array";
            structure.elements = elements;
            structure.changedIndices = variable.changed
                    ? changedIndices(variable.oldValue, elements) : List.of();
            structure.cells = new ArrayList<>(elements.size());
            for (int i = 0; i < elements.size(); i++) {
                SceneState.Cell cell = new SceneState.Cell();
                cell.index = i;
                cell.x = i * ARRAY_CELL_STEP;
                cell.y = 0;
                cell.w = ARRAY_CELL_WIDTH;
                cell.h = ARRAY_CELL_HEIGHT;
                structure.cells.add(cell);
            }
            scene.structures.add(structure);
        }

        if (scene.activeFile != null) {
            scene.visibleFiles.add(scene.activeFile);
        } else {
            for (FrameState frame : stack) {
                if (frame.file != null && !scene.visibleFiles.contains(frame.file)) {
                    scene.visibleFiles.add(frame.file);
                }
            }
        }

        scene.tone = toneFor(scene.eventSignature, tones);
        scene.animation = animationFor(event.type);
        return scene;
    }

    private static FrameState findFrame(Deque<FrameState> stack, TraceEvent event) {
        for (FrameState frame : stack) {
            if (equals(frame.className, event.className) && equals(frame.methodName, event.methodName)) {
                return frame;
            }
        }
        return stack.peek();
    }

    private static String activeFile(Deque<FrameState> stack) {
        for (FrameState frame : stack) {
            if (frame.file != null) {
                return frame.file;
            }
        }
        return null;
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String labelFor(TraceEvent event) {
        String owner = simpleName(event.className) + "." + (event.methodName == null ? "?" : event.methodName);
        return switch (event.type) {
            case METHOD_CALL -> owner + "(" + String.join(", ", event.arguments) + ")";
            case METHOD_RETURN -> owner + " \u2192 " + (event.returnValue == null ? "void" : event.returnValue);
            case ASSIGNMENT -> event.variable == null ? "assignment"
                    : event.oldValue == null
                            ? event.variable + " = " + event.newValue
                            : event.variable + ": " + event.oldValue + " \u2192 " + event.newValue;
            default -> event.type.name();
        };
    }

    private static String subtitleFor(String file, int line) {
        if (file == null && line <= 0) {
            return "Execution trace";
        }
        if (file == null) {
            return "Line " + line;
        }
        return line > 0 ? "Line " + line + " \u00b7 " + file : file;
    }

    private static String signatureFor(EventType type) {
        return switch (type) {
            case METHOD_CALL -> "call";
            case METHOD_RETURN -> "return";
            case ASSIGNMENT -> "assignment";
            case COMPARISON -> "compare";
            case BRANCH -> "branch";
            case LOOP_ITERATION -> "loop";
            case CROSS_FILE_ACCESS -> "access:cross-file";
        };
    }

    private static ToneSpec toneFor(String signature, ToneProvider tones) {
        if (tones == null) {
            return null;
        }
        try {
            return tones.toneFor(signature);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static SceneState.Animation animationFor(EventType type) {
        return switch (type) {
            case METHOD_CALL -> new SceneState.Animation("frame_push", 240);
            case METHOD_RETURN -> new SceneState.Animation("frame_pop", 220);
            case ASSIGNMENT -> new SceneState.Animation("value_change", 200);
            default -> new SceneState.Animation("event", 180);
        };
    }

    private static List<Integer> changedIndices(String oldValue, List<String> newElements) {
        List<String> oldElements = parseArray(oldValue);
        if (oldElements == null) {
            List<Integer> all = new ArrayList<>(newElements.size());
            for (int i = 0; i < newElements.size(); i++) {
                all.add(i);
            }
            return all;
        }
        List<Integer> changed = new ArrayList<>();
        int size = Math.max(oldElements.size(), newElements.size());
        for (int i = 0; i < size; i++) {
            String before = i < oldElements.size() ? oldElements.get(i) : null;
            String after = i < newElements.size() ? newElements.get(i) : null;
            if (!equals(before, after)) {
                changed.add(i);
            }
        }
        return changed;
    }

    /**
     * Parse the deterministic array format produced by ValueFormatter:
     * {@code [1, 2, 3]} or {@code ["a", "b"]}. Returns null for non-arrays.
     */
    static List<String> parseArray(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            return null;
        }
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> elements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                current.append(c);
            } else if (c == '[' || c == '(' || c == '{') {
                depth++;
                current.append(c);
            } else if (c == ']' || c == ')' || c == '}') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                elements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        elements.add(current.toString().trim());
        return elements;
    }

    private static String simpleName(String className) {
        if (className == null || className.isEmpty()) {
            return "?";
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String serialize(CodeSubmission submission, String status, String errorMessage,
                                     List<SceneState> frames) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\"timeline_schema_version\":").append(TIMELINE_SCHEMA_VERSION);
        sb.append(",\"trace_schema_version\":").append(TRACE_SCHEMA_VERSION);
        sb.append(",\"status\":").append(Json.escape(status));
        if (errorMessage != null && !errorMessage.isBlank()) {
            sb.append(",\"error_message\":").append(Json.escape(errorMessage));
        }
        appendSources(sb, submission);
        sb.append(",\"frame_count\":").append(frames.size());
        sb.append(",\"frames\":[");
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendFrame(sb, frames.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendFrame(StringBuilder sb, SceneState frame) {
        sb.append("{\"index\":").append(frame.index)
                .append(",\"seq\":").append(frame.seq)
                .append(",\"event\":").append(frame.eventJson)
                .append(",\"event_type\":").append(Json.escape(frame.eventType))
                .append(",\"event_signature\":").append(Json.escape(frame.eventSignature))
                .append(",\"active_file\":").append(Json.escape(frame.activeFile))
                .append(",\"active_line\":").append(frame.activeLine)
                .append(",\"label\":{\"title\":").append(Json.escape(frame.labelTitle))
                .append(",\"subtitle\":").append(Json.escape(frame.labelSubtitle)).append('}');

        sb.append(",\"call_stack\":[");
        for (int i = 0; i < frame.callStack.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            SceneState.CallFrame call = frame.callStack.get(i);
            sb.append("{\"class\":").append(Json.escape(call.className))
                    .append(",\"method\":").append(Json.escape(call.methodName))
                    .append(",\"file\":").append(Json.escape(call.file))
                    .append(",\"line\":").append(call.line)
                    .append(",\"depth\":").append(call.depth);
            if (call.arguments != null && !call.arguments.isEmpty()) {
                sb.append(",\"args\":[");
                for (int j = 0; j < call.arguments.size(); j++) {
                    if (j > 0) {
                        sb.append(',');
                    }
                    sb.append(Json.escape(call.arguments.get(j)));
                }
                sb.append(']');
            }
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"variables\":[");
        for (int i = 0; i < frame.variables.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            SceneState.Variable variable = frame.variables.get(i);
            sb.append("{\"name\":").append(Json.escape(variable.name))
                    .append(",\"value\":").append(Json.escape(variable.value))
                    .append(",\"changed\":").append(variable.changed);
            if (variable.changed) {
                sb.append(",\"old\":").append(Json.escape(variable.oldValue))
                        .append(",\"new\":").append(Json.escape(variable.newValue));
            }
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"structures\":[");
        for (int i = 0; i < frame.structures.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            SceneState.Structure structure = frame.structures.get(i);
            sb.append("{\"name\":").append(Json.escape(structure.name))
                    .append(",\"kind\":").append(Json.escape(structure.kind))
                    .append(",\"elements\":[");
            for (int j = 0; j < structure.elements.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append(Json.escape(structure.elements.get(j)));
            }
            sb.append("],\"changed_indices\":[");
            for (int j = 0; j < structure.changedIndices.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append(structure.changedIndices.get(j));
            }
            sb.append("],\"geometry\":{\"cells\":[");
            for (int j = 0; j < structure.cells.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                SceneState.Cell cell = structure.cells.get(j);
                sb.append("{\"index\":").append(cell.index)
                        .append(",\"x\":").append(cell.x)
                        .append(",\"y\":").append(cell.y)
                        .append(",\"w\":").append(cell.w)
                        .append(",\"h\":").append(cell.h).append('}');
            }
            sb.append("]}}");
        }
        sb.append(']');

        sb.append(",\"visible_files\":[");
        for (int i = 0; i < frame.visibleFiles.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Json.escape(frame.visibleFiles.get(i)));
        }
        sb.append("],\"connectors\":[]");
        sb.append(",\"stdout_visible\":").append(Json.escape(frame.stdoutVisible));

        sb.append(",\"tone\":");
        if (frame.tone == null) {
            sb.append("null");
        } else {
            ToneSpec tone = frame.tone;
            sb.append("{\"signature\":").append(Json.escape(tone.signature))
                    .append(",\"frequency_hz\":")
                    .append(String.format(Locale.ROOT, "%.6f", tone.frequencyHz))
                    .append(",\"oscillator\":").append(Json.escape(tone.oscillator))
                    .append(",\"duration_ms\":").append(tone.durationMs)
                    .append(",\"note_index\":").append(tone.noteIndex)
                    .append(",\"midi_note\":").append(tone.midiNote)
                    .append(",\"hash\":").append(Json.escape(tone.hash))
                    .append('}');
        }

        sb.append(",\"animation\":{\"kind\":").append(Json.escape(frame.animation.kind))
                .append(",\"duration_ms\":").append(frame.animation.durationMs).append("}}");
    }

    private static void appendSources(StringBuilder sb, CodeSubmission submission) {
        sb.append(",\"sources\":[");
        for (int i = 0; i < submission.files.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            JavaSourceFile file = submission.files.get(i);
            sb.append("{\"file_name\":").append(Json.escape(file.fileName))
                    .append(",\"is_main_file\":").append(file.isMainFile)
                    .append(",\"content\":").append(Json.escape(file.source == null ? "" : file.source))
                    .append('}');
        }
        sb.append(']');
    }

    private static String error(CodeSubmission submission, String status, String errorStatus, String message) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"timeline_schema_version\":").append(TIMELINE_SCHEMA_VERSION)
                .append(",\"trace_schema_version\":").append(TRACE_SCHEMA_VERSION)
                .append(",\"status\":").append(Json.escape(status))
                .append(",\"error\":").append(Json.escape(errorStatus))
                .append(",\"error_message\":").append(Json.escape(message));
        appendSources(sb, submission);
        sb.append(",\"frame_count\":0,\"frames\":[]}");
        return sb.toString();
    }

    private static final class FrameState {
        final String className;
        final String methodName;
        final String file;
        final List<String> arguments;
        final Map<String, String> variables = new LinkedHashMap<>();
        int line;

        FrameState(TraceEvent event) {
            this.className = event.className;
            this.methodName = event.methodName;
            this.file = event.file;
            this.line = event.line;
            this.arguments = event.arguments;
        }
    }
}

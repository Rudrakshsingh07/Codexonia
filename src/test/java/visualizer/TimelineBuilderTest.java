package visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import runner.CodeSubmission;
import runner.Json;
import runner.TraceEvent;
import runner.TraceSerializer;

class TimelineBuilderTest {

    private static final ToneProvider TONES = signature ->
            new ToneSpec(signature, 261.625565, "sine", 200, 1, 60, "deadbeef");

    @Test
    void buildsCallStackVariablesArraysAndTones() {
        List<TraceEvent> events = List.of(
                TraceEvent.call(1, "Sample.java", "Sample", "main", 3, List.of("[]")),
                TraceEvent.assignment(2, "Sample.java", "Sample", "main", 3, "args", null, "[]"),
                TraceEvent.assignment(3, "Sample.java", "Sample", "main", 3, "x", null, "2"),
                TraceEvent.call(4, "Sample.java", "Sample", "add", 12, List.of("2", "3")),
                TraceEvent.assignment(5, "Sample.java", "Sample", "add", 12, "a", null, "2"),
                TraceEvent.assignment(6, "Sample.java", "Sample", "add", 12, "b", null, "3"),
                TraceEvent.assignment(7, "Sample.java", "Sample", "add", 13, "c", null, "5"),
                TraceEvent.returnEvent(8, "Sample.java", "Sample", "add", 13, "5"),
                TraceEvent.assignment(9, "Sample.java", "Sample", "main", 4, "y", null, "5"),
                TraceEvent.assignment(10, "Sample.java", "Sample", "main", 5, "nums", null, "[1, 2, 3]"),
                TraceEvent.assignment(11, "Sample.java", "Sample", "main", 6, "nums", "[1, 2, 3]", "[5, 2, 3]"),
                TraceEvent.returnEvent(12, "Sample.java", "Sample", "main", 9, null)
        );
        CodeSubmission submission = CodeSubmission.singleFile("Sample.java", "public class Sample {}");
        String traceJson = TraceSerializer.toJson(submission, events, "5:5\n", "COMPLETE");

        String timelineJson = TimelineBuilder.build(submission, traceJson, TONES);
        Map<String, Object> timeline = map(Json.parse(timelineJson));
        List<Object> frames = list(timeline.get("frames"));

        assertEquals(12, number(timeline.get("frame_count")));
        assertEquals(12, frames.size());
        assertEquals("COMPLETE", timeline.get("status"));
        assertEquals("public class Sample {}", map(list(timeline.get("sources")).get(0)).get("content"));

        Map<String, Object> first = map(frames.get(0));
        assertEquals("METHOD_CALL", first.get("event_type"));
        assertEquals("call", first.get("event_signature"));
        assertEquals(1, list(first.get("call_stack")).size());
        assertEquals("Sample.main([])", map(first.get("label")).get("title"));
        assertEquals("frame_push", map(first.get("animation")).get("kind"));
        assertEquals("call", map(first.get("tone")).get("signature"));

        Map<String, Object> insideAdd = map(frames.get(3));
        assertEquals(2, list(insideAdd.get("call_stack")).size());
        assertEquals("Sample.add(2, 3)", map(insideAdd.get("label")).get("title"));
        Map<String, Object> topFrame = map(list(insideAdd.get("call_stack")).get(1));
        assertEquals("add", topFrame.get("method"));
        assertEquals(1, number(topFrame.get("depth")));

        Map<String, Object> cAssigned = map(frames.get(6));
        Map<String, Object> c = variable(cAssigned, "c");
        assertEquals("5", c.get("value"));
        assertEquals(Boolean.TRUE, c.get("changed"));

        Map<String, Object> addReturn = map(frames.get(7));
        assertEquals(2, list(addReturn.get("call_stack")).size());
        assertEquals("Sample.add \u2192 5", map(addReturn.get("label")).get("title"));

        Map<String, Object> backInMain = map(frames.get(8));
        assertEquals(1, list(backInMain.get("call_stack")).size());
        assertEquals("Sample.main", labelOwner(backInMain));

        Map<String, Object> numsChanged = map(frames.get(10));
        Map<String, Object> structure = structure(numsChanged, "nums");
        assertEquals("array", structure.get("kind"));
        assertEquals(List.of("5", "2", "3"), list(structure.get("elements")));
        assertEquals(List.of(0L), list(structure.get("changed_indices")));
        Map<String, Object> cell = map(list(map(structure.get("geometry")).get("cells")).get(0));
        assertEquals(48, number(cell.get("w")));
        assertEquals(40, number(cell.get("h")));

        Map<String, Object> last = map(frames.get(11));
        assertEquals("5:5\n", last.get("stdout_visible"));
        assertEquals(1, list(last.get("call_stack")).size());
        assertEquals(1, list(last.get("structures")).size());
    }

    @Test
    void attachesTimelineWithoutChangingTraceFields() {
        CodeSubmission submission = CodeSubmission.singleFile("A.java", "class A {}");
        String traceJson = TraceSerializer.toJson(submission,
                List.of(TraceEvent.call(1, "A.java", "A", "main", 1, List.of())), "", "COMPLETE");
        String timelineJson = TimelineBuilder.build(submission, traceJson, TONES);
        Map<String, Object> attached = map(Json.parse(TimelineBuilder.attach(traceJson, timelineJson)));

        assertEquals(1, number(attached.get("schema_version")));
        assertEquals(1, number(attached.get("event_count")));
        assertTrue(attached.get("timeline") instanceof Map);
    }

    @Test
    void emptyTraceStillProducesAUsableTimeline() {
        CodeSubmission submission = CodeSubmission.singleFile("Empty.java", "class Empty {}");
        String traceJson = TraceSerializer.toJson(submission, List.of(), null, "NO_MAIN_METHOD",
                "No main method");
        String timelineJson = TimelineBuilder.build(submission, traceJson, TONES);
        Map<String, Object> timeline = map(Json.parse(timelineJson));

        assertEquals(0, number(timeline.get("frame_count")));
        assertEquals(0, list(timeline.get("frames")).size());
        assertEquals("NO_MAIN_METHOD", timeline.get("status"));
        assertEquals("No main method", timeline.get("error_message"));
    }

    @Test
    void parsesArrayValuesWithoutSplittingQuotedCommasOrNestedArrays() {
        assertEquals(List.of("\"a,b\"", "\"c\""), TimelineBuilder.parseArray("[\"a,b\", \"c\"]"));
        assertEquals(List.of("1", "[2, 3]", "4"), TimelineBuilder.parseArray("[1, [2, 3], 4]"));
        assertEquals(List.of(), TimelineBuilder.parseArray("[]"));
        assertNull(TimelineBuilder.parseArray("42"));
        assertFalse(TimelineBuilder.parseArray("[1, 2]").isEmpty());
    }

    @Test
    void recordsEverySignatureWhenBuildingFrames() {
        List<String> seen = new ArrayList<>();
        ToneProvider tones = signature -> {
            seen.add(signature);
            return new ToneSpec(signature, 220, "sine", 150, 0, 57, "hash");
        };
        CodeSubmission submission = CodeSubmission.singleFile("A.java", "class A {}");
        String traceJson = TraceSerializer.toJson(submission, List.of(
                TraceEvent.call(1, "A.java", "A", "main", 1, List.of()),
                TraceEvent.assignment(2, "A.java", "A", "main", 2, "x", null, "1"),
                TraceEvent.returnEvent(3, "A.java", "A", "main", 3, null)
        ), "", "COMPLETE");

        TimelineBuilder.build(submission, traceJson, tones);
        assertEquals(List.of("call", "assignment", "return"), seen);
        assertNotNull(seen);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static Map<String, Object> variable(Map<String, Object> frame, String name) {
        for (Object item : list(frame.get("variables"))) {
            Map<String, Object> variable = map(item);
            if (name.equals(variable.get("name"))) {
                return variable;
            }
        }
        throw new AssertionError("missing variable " + name);
    }

    private static Map<String, Object> structure(Map<String, Object> frame, String name) {
        for (Object item : list(frame.get("structures"))) {
            Map<String, Object> structure = map(item);
            if (name.equals(structure.get("name"))) {
                return structure;
            }
        }
        throw new AssertionError("missing structure " + name);
    }

    private static String labelOwner(Map<String, Object> frame) {
        Map<String, Object> top = map(list(frame.get("call_stack")).get(0));
        return top.get("class") + "." + top.get("method");
    }
}

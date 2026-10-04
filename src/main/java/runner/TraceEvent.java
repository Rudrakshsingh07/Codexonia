package runner;

import java.util.List;
import java.util.Map;

/** One recorded execution event. Field names mirror the seeded ADR-8 trace contract v1. */
public final class TraceEvent {
    public final int seq;
    public final EventType type;
    public final String file;
    public final String className;
    public final String methodName;
    public final int line; // -1 when not applicable
    public final String variable;   // ASSIGNMENT only
    public final String oldValue;   // ASSIGNMENT only (null on first sight of a variable)
    public final String newValue;   // ASSIGNMENT only
    public final List<String> arguments; // METHOD_CALL only
    public final String returnValue;     // METHOD_RETURN only

    private TraceEvent(int seq, EventType type, String file, String className, String methodName,
                       int line, String variable, String oldValue, String newValue,
                       List<String> arguments, String returnValue) {
        this.seq = seq;
        this.type = type;
        this.file = file;
        this.className = className;
        this.methodName = methodName;
        this.line = line;
        this.variable = variable;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.arguments = arguments == null ? List.of() : arguments;
        this.returnValue = returnValue;
    }

    public static TraceEvent call(int seq, String file, String className, String methodName, int line, List<String> arguments) {
        return new TraceEvent(seq, EventType.METHOD_CALL, file, className, methodName, line,
                null, null, null, arguments, null);
    }

    public static TraceEvent returnEvent(int seq, String file, String className, String methodName, int line, String returnValue) {
        return new TraceEvent(seq, EventType.METHOD_RETURN, file, className, methodName, line,
                null, null, null, null, returnValue);
    }

    public static TraceEvent assignment(int seq, String file, String className, String methodName, int line,
                                        String variable, String oldValue, String newValue) {
        return new TraceEvent(seq, EventType.ASSIGNMENT, file, className, methodName, line,
                variable, oldValue, newValue, null, null);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"seq\":").append(seq)
                .append(",\"type\":").append(Json.escape(type.name()))
                .append(",\"file\":").append(Json.escape(file))
                .append(",\"class\":").append(Json.escape(className))
                .append(",\"method\":").append(Json.escape(methodName))
                .append(",\"line\":").append(line);
        if (variable != null) {
            sb.append(",\"var\":").append(Json.escape(variable));
        }
        if (oldValue != null) {
            sb.append(",\"old\":").append(Json.escape(oldValue));
        }
        if (newValue != null) {
            sb.append(",\"new\":").append(Json.escape(newValue));
        }
        if (!arguments.isEmpty()) {
            sb.append(",\"args\":[");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(Json.escape(arguments.get(i)));
            }
            sb.append(']');
        }
        if (returnValue != null) {
            sb.append(",\"ret\":").append(Json.escape(returnValue));
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    public static TraceEvent fromJson(Map<String, Object> m) {
        int seq = ((Number) m.get("seq")).intValue();
        EventType type = EventType.valueOf((String) m.get("type"));
        String file = (String) m.get("file");
        String className = (String) m.get("class");
        String methodName = (String) m.get("method");
        int line = ((Number) m.get("line")).intValue();
        String variable = (String) m.get("var");
        String oldValue = (String) m.get("old");
        String newValue = (String) m.get("new");
        Object argsRaw = m.get("args");
        List<String> arguments = argsRaw == null ? List.of() : (List<String>) argsRaw;
        String returnValue = (String) m.get("ret");
        return new TraceEvent(seq, type, file, className, methodName, line,
                variable, oldValue, newValue, arguments, returnValue);
    }
}

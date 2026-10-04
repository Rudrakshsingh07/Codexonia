package runner;

/** Minimal JSON reader/writer for the runner's internal contracts (JSONL event stream + run-result). */
public final class Json {

    private Json() {}

    public static String escape(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static int pos;
    private static String text;

    /** Parse a JSON text into Map<String,Object> / List<Object> / String / Long / Double / Boolean / null. */
    public static synchronized Object parse(String s) {
        text = s;
        pos = 0;
        Object v = value();
        skipWs();
        if (pos != text.length()) {
            throw new IllegalArgumentException("Trailing JSON at " + pos);
        }
        return v;
    }

    private static void skipWs() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private static Object value() {
        skipWs();
        if (pos >= text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> {
                expect("true");
                yield Boolean.TRUE;
            }
            case 'f' -> {
                expect("false");
                yield Boolean.FALSE;
            }
            case 'n' -> {
                expect("null");
                yield null;
            }
            default -> number();
        };
    }

    private static void expect(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw new IllegalArgumentException("Expected '" + literal + "' at " + pos);
        }
        pos += literal.length();
    }

    private static java.util.Map<String, Object> object() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (pos < text.length() && text.charAt(pos) == '}') {
            pos++;
            return m;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            if (pos >= text.length() || text.charAt(pos) != ':') {
                throw new IllegalArgumentException("Expected ':' at " + pos);
            }
            pos++;
            m.put(key, value());
            skipWs();
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unclosed object");
            }
            char c = text.charAt(pos++);
            if (c == '}') {
                return m;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Expected ',' or '}' at " + (pos - 1));
            }
        }
    }

    private static java.util.List<Object> array() {
        java.util.List<Object> list = new java.util.ArrayList<>();
        pos++; // [
        skipWs();
        if (pos < text.length() && text.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWs();
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unclosed array");
            }
            char c = text.charAt(pos++);
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Expected ',' or ']' at " + (pos - 1));
            }
        }
    }

    private static String string() {
        if (text.charAt(pos) != '"') {
            throw new IllegalArgumentException("Expected string at " + pos);
        }
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unclosed string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    throw new IllegalArgumentException("Bad escape");
                }
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape '\\" + esc + "'");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private static Number number() {
        int start = pos;
        if (text.charAt(pos) == '-') {
            pos++;
        }
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.'
                || text.charAt(pos) == 'e' || text.charAt(pos) == 'E' || text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
            pos++;
        }
        String num = text.substring(start, pos);
        if (num.contains(".") || num.contains("e") || num.contains("E")) {
            return Double.valueOf(num);
        }
        return Long.valueOf(num);
    }
}

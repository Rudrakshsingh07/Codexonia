package visualizer;

import java.util.ArrayList;
import java.util.List;

/** One precomputed visual state. The browser only paints these fields. */
public final class SceneState {
    public int index;
    public int seq;
    public String eventJson;
    public String eventType;
    public String eventSignature;
    public String activeFile;
    public int activeLine;
    public String labelTitle;
    public String labelSubtitle;
    public final List<CallFrame> callStack = new ArrayList<>();
    public final List<Variable> variables = new ArrayList<>();
    public final List<Structure> structures = new ArrayList<>();
    public final List<String> visibleFiles = new ArrayList<>();
    public String stdoutVisible = "";
    public ToneSpec tone;
    public Animation animation;

    public static final class CallFrame {
        public String className;
        public String methodName;
        public String file;
        public int line;
        public int depth;
        public List<String> arguments;
    }

    public static final class Variable {
        public String name;
        public String value;
        public String oldValue;
        public String newValue;
        public boolean changed;
    }

    public static final class Structure {
        public String name;
        public String kind;
        public List<String> elements;
        public List<Integer> changedIndices;
        public List<Cell> cells;
    }

    public static final class Cell {
        public int index;
        public int x;
        public int y;
        public int w;
        public int h;
    }

    public static final class Animation {
        public String kind;
        public int durationMs;

        public Animation(String kind, int durationMs) {
            this.kind = kind;
            this.durationMs = durationMs;
        }
    }
}

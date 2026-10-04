package runner.tracer;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;

import java.util.List;

/** Deterministic, cap-safe rendering of JDI values into mechanical trace labels. */
public final class ValueFormatter {

    private ValueFormatter() {}

    public static String format(Value v) {
        if (v == null) {
            return null;
        }
        try {
            if (v instanceof PrimitiveValue pv) {
                return pv.toString();
            }
            if (v instanceof StringReference sr) {
                String s = sr.value();
                if (s.length() > 64) {
                    s = s.substring(0, 64) + "...";
                }
                return "\"" + s + "\"";
            }
            if (v instanceof ArrayReference ar) {
                List<Value> elts = ar.getValues();
                StringBuilder sb = new StringBuilder("[");
                int limit = Math.min(elts.size(), 8);
                for (int i = 0; i < limit; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(format(elts.get(i)));
                }
                if (elts.size() > limit) {
                    sb.append(", ...");
                }
                return sb.append(']').toString();
            }
            if (v instanceof ObjectReference or) {
                return or.type().name();
            }
            return v.toString();
        } catch (Throwable t) {
            return "<unreadable>";
        }
    }
}

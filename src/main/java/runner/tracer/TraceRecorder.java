package runner.tracer;

import com.sun.jdi.Location;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import runner.TraceEvent;

/**
 * Consumes JDI events and produces the ordered TraceEvent stream (ADR-1, FR6 subset for v1).
 *
 * ASSIGNMENT events come from diffing visible local variables between line-granularity
 * step events. METHOD_CALL/METHOD_RETURN come from entry/exit events. Class filters
 * keep JDK internals out of the trace.
 */
public final class TraceRecorder {

    private final PrintWriter out;
    private final Set<String> classFilters;
    private final VirtualMachine vm;
    private final int maxEvents;

    private final Map<ThreadReference, Deque<Map<String, String>>> localsStack = new HashMap<>();
    private final Map<ThreadReference, StepRequest> pendingSteps = new HashMap<>();

    private int seq;
    private volatile String terminalFailure;

    public TraceRecorder(PrintWriter out, Set<String> classFilters, VirtualMachine vm, int maxEvents) {
        this.out = out;
        this.classFilters = classFilters;
        this.vm = vm;
        this.maxEvents = maxEvents;
    }

    public int eventCount() {
        return seq;
    }

    public String terminalFailure() {
        return terminalFailure;
    }

    private void emit(TraceEvent e) {
        out.println(e.toJson());
        seq++;
        if (seq % 200 == 0) {
            out.flush();
        }
        if (seq >= maxEvents && terminalFailure == null) {
            terminalFailure = "EVENT_CAP";
            try {
                vm.exit(3);
            } catch (Throwable ignored) {
            }
        }
    }

    public void onMethodEntry(MethodEntryEvent e) {
        Location loc = e.location();
        String method = loc.method().name();
        if ("<clinit>".equals(method)) {
            return;
        }
        List<String> args = new ArrayList<>();
        try {
            StackFrame f = e.thread().frame(0);
            for (Value v : f.getArgumentValues()) {
                args.add(ValueFormatter.format(v));
            }
        } catch (Throwable ignored) {
            args.clear();
        }
        emit(TraceEvent.call(seq + 1, sourceName(loc), typeName(loc), method, loc.lineNumber(), args));
        localsStack.computeIfAbsent(e.thread(), t -> new ArrayDeque<>()).push(new LinkedHashMap<>());
        ensureStep(e.thread());
    }

    public void onStep(StepEvent e) {
        ThreadReference t = e.thread();
        try {
            StackFrame f = t.frame(0);
            Location loc = f.location();
            Deque<Map<String, String>> stack = localsStack.computeIfAbsent(t, x -> new ArrayDeque<>());
            Map<String, String> prev = stack.isEmpty() ? Map.of() : stack.peek();
            Map<String, String> cur = new LinkedHashMap<>();
            for (LocalVariable lv : f.visibleVariables()) {
                cur.put(lv.name(), ValueFormatter.format(f.getValue(lv)));
            }
            for (Map.Entry<String, String> en : cur.entrySet()) {
                String old = prev.get(en.getKey());
                if (old == null || !old.equals(en.getValue())) {
                    emit(TraceEvent.assignment(seq + 1, sourceName(loc), typeName(loc),
                            loc.method().name(), loc.lineNumber(), en.getKey(), old, en.getValue()));
                }
            }
            if (!stack.isEmpty()) {
                stack.pop();
            }
            stack.push(cur);
        } catch (Throwable ignored) {
        }
        ensureStep(t);
    }

    public void onMethodExit(MethodExitEvent e) {
        String method = e.location().method().name();
        if ("<clinit>".equals(method)) {
            return;
        }
        Location loc = e.location();
        Value rv = null;
        try {
            rv = e.returnValue();
        } catch (Throwable ignored) {
        }
        String formatted = ValueFormatter.format(rv);
        emit(TraceEvent.returnEvent(seq + 1, sourceName(loc), typeName(loc), method,
                loc.lineNumber(), "<void value>".equals(formatted) ? null : formatted));
        Deque<Map<String, String>> stack = localsStack.get(e.thread());
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
        }
        ensureStep(e.thread());
    }

    private void ensureStep(ThreadReference t) {
        try {
            EventRequestManager em = vm.eventRequestManager();
            StepRequest old = pendingSteps.remove(t);
            if (old != null) {
                try {
                    em.deleteEventRequest(old);
                } catch (Throwable ignored) {
                }
            }
            StepRequest sr = em.createStepRequest(t, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
            for (String cf : classFilters) {
                sr.addClassFilter(cf);
            }
            sr.setSuspendPolicy(StepRequest.SUSPEND_EVENT_THREAD);
            sr.enable();
            pendingSteps.put(t, sr);
        } catch (Throwable ignored) {
        }
    }

    private static String sourceName(Location loc) {
        try {
            return loc.sourceName();
        } catch (Throwable t) {
            return typeName(loc) + ".java";
        }
    }

    private static String typeName(Location loc) {
        return loc.declaringType().name();
    }
}

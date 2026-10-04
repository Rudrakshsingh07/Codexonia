package runner.tracer;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import runner.Json;

/** Runs INSIDE the sandbox image: compiles the submission, launches it under JDI, records trace.jsonl. */
public final class TracerMain {

    public static void main(String[] args) {
        int exit = 0;
        try {
            exit = run(args);
        } catch (Throwable t) {
            try {
                Paths.get(args[3]).getParent().toFile().mkdirs();
                Files.write(Paths.get(args[3]), ("{\"status\":\"INTERNAL_ERROR\",\"exit_code\":2,\"stdout\":\"\",\"stderr\":\"\",\"error_message\":"
                        + Json.escape(String.valueOf(t)) + "}").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
            exit = 2;
        }
        System.exit(exit);
    }

    private static int run(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("Usage: TracerMain <srcDir> <classesDir> <trace.jsonl> <run-result.json> <mainClass> <maxEvents> <timeoutSeconds>");
            return 2;
        }
        Path srcDir = Paths.get(args[0]);
        Path classesDir = Paths.get(args[1]);
        Path tracePath = Paths.get(args[2]);
        Path resultPath = Paths.get(args[3]);
        String mainClass = args[4];
        int maxEvents = Integer.parseInt(args[5]);
        int timeoutSeconds = Integer.parseInt(args[6]);

        Files.createDirectories(classesDir);
        if (!Files.exists(tracePath)) {
            Files.createFile(tracePath);
        }

        // 1. Compile (sandbox compile: errors here mean the submission is rejected, like javac on the host)
        List<Path> javaFiles;
        try (Stream<Path> s = Files.walk(srcDir)) {
            javaFiles = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        if (javaFiles.isEmpty()) {
            writeResult(resultPath, "COMPILE_ERROR", 1, "", "", "No .java files in submission");
            return 0;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(
                    javaFiles.stream().map(Path::toFile).collect(Collectors.toList()));
            List<String> options = List.of("-g", "-d", classesDir.toString());
            boolean ok = compiler.getTask(null, fm, diags, options, null, units).call();
            if (!ok) {
                StringBuilder errs = new StringBuilder();
                for (var d : diags.getDiagnostics()) {
                    errs.append(d.getSource() == null ? "<unknown>" : d.getSource().getName())
                            .append(':').append(d.getLineNumber()).append(": ")
                            .append(d.getMessage(null)).append('\n');
                }
                writeResult(resultPath, "COMPILE_ERROR", 1, "", "", errs.toString());
                return 0;
            }
        }

        // 2. Class filters: every compiled student class, so JDI ignores JDK internals.
        Set<String> classFilters = new HashSet<>();
        try (Stream<Path> s = Files.walk(classesDir)) {
            s.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String rel = classesDir.relativize(p).toString();
                classFilters.add(rel.substring(0, rel.length() - ".class".length()).replace(File.separatorChar, '.'));
            });
        }

        // 3. Launch the debuggee JVM under JDI.
        LaunchingConnector connector = null;
        for (var c : Bootstrap.virtualMachineManager().launchingConnectors()) {
            if ("com.sun.jdi.CommandLineLaunch".equals(c.name())) {
                connector = c;
                break;
            }
        }
        if (connector == null) {
            writeResult(resultPath, "INTERNAL_ERROR", 2, "", "", "No CommandLineLaunch connector");
            return 0;
        }
        Map<String, ? extends Connector.Argument> argMap = connector.defaultArguments();
        argMap.get("main").setValue(mainClass);
        argMap.get("options").setValue("-cp " + classesDir);
        argMap.get("suspend").setValue("true");
        VirtualMachine vm = connector.launch(argMap);

        TraceRecorder recorder;
        PrintWriter traceWriter = new PrintWriter(Files.newBufferedWriter(tracePath, StandardCharsets.UTF_8));
        recorder = new TraceRecorder(traceWriter, classFilters, vm, maxEvents);

        EventRequestManager em = vm.eventRequestManager();
        MethodEntryRequest menr = em.createMethodEntryRequest();
        MethodExitRequest mexr = em.createMethodExitRequest();
        for (String cf : classFilters) {
            menr.addClassFilter(cf);
            mexr.addClassFilter(cf);
        }
        menr.enable();
        mexr.enable();

        // Stream-collectors cap + tee the debuggee's stdout/stderr.
        StreamCollector stdoutColl = new StreamCollector(vm.process().getInputStream(), System.out, 64 * 1024);
        StreamCollector stderrColl = new StreamCollector(vm.process().getErrorStream(), System.err, 64 * 1024);
        stdoutColl.start();
        stderrColl.start();

        final StringBuilder terminal = new StringBuilder(); // set once, by watchdog/recorder caps
        final VirtualMachine vmRef = vm;
        Thread watchdog = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (terminal.length() == 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    return;
                }
                if (stdoutColl.exceeded() || stderrColl.exceeded()) {
                    terminal.append("OUTPUT_CAP");
                    try {
                        vmRef.exit(6);
                    } catch (Throwable ignored) {
                    }
                    return;
                }
                if (System.nanoTime() > deadline) {
                    terminal.append("TIMEOUT");
                    try {
                        vmRef.exit(124);
                    } catch (Throwable ignored) {
                    }
                    return;
                }
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        EventQueue queue = vm.eventQueue();
        boolean vmGone = false;
        while (!vmGone) {
            EventSet set;
            try {
                set = queue.remove();
            } catch (InterruptedException e) {
                return 1;
            }
            for (Event e : set) {
                try {
                    if (e instanceof MethodEntryEvent mee) {
                        recorder.onMethodEntry(mee);
                    } else if (e instanceof StepEvent se) {
                        recorder.onStep(se);
                    } else if (e instanceof MethodExitEvent mxe) {
                        recorder.onMethodExit(mxe);
                    } else if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) {
                        vmGone = true;
                    }
                } catch (Throwable t) {
                    System.err.println("tracer: event handling failed: " + t);
                }
            }
            set.resume();
            if (recorder.terminalFailure() != null && terminal.length() == 0) {
                terminal.append(recorder.terminalFailure());
            }
        }

        try {
            vm.process().waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return 1;
        }
        int code = vm.process().exitValue();
        traceWriter.flush();
        traceWriter.close();
        stdoutColl.join();
        stderrColl.join();

        String status;
        String errorMessage = null;
        if (terminal.length() > 0) {
            status = terminal.toString();
        } else if (recorder.terminalFailure() != null) {
            status = recorder.terminalFailure();
        } else if (code == 0) {
            status = "COMPLETE";
        } else {
            status = "RUNTIME_ERROR";
            String err = stderrColl.text();
            errorMessage = err.length() > 1024 ? err.substring(err.length() - 1024) : err;
        }
        writeResult(resultPath, status, code, stdoutColl.text(), stderrColl.text(), errorMessage);
        return 0;
    }

    private static void writeResult(Path path, String status, int exitCode, String stdout, String stderr, String errorMessage) throws IOException {
        StringBuilder sb = new StringBuilder("{\"status\":")
                .append(Json.escape(status))
                .append(",\"exit_code\":").append(exitCode)
                .append(",\"stdout\":").append(Json.escape(stdout))
                .append(",\"stderr\":").append(Json.escape(stderr));
        if (errorMessage != null && !errorMessage.isEmpty()) {
            sb.append(",\"error_message\":").append(Json.escape(errorMessage));
        }
        sb.append('}');
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Drains a pipe into a capped buffer while also forwarding it to a live sink (stdout/stderr). */
    private static final class StreamCollector {
        private final InputStream in;
        private final OutputStream forward;
        private final int cap;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean overflowed;
        private Thread thread;

        StreamCollector(InputStream in, OutputStream forward, int cap) {
            this.in = in;
            this.forward = forward;
            this.cap = cap;
        }

        void start() {
            thread = new Thread(this::drain, "stream-collector");
            thread.setDaemon(true);
            thread.start();
        }

        void join() throws InterruptedException {
            thread.join(2000);
        }

        boolean exceeded() {
            return overflowed;
        }

        String text() {
            synchronized (buf) {
                return buf.toString(StandardCharsets.UTF_8);
            }
        }

        private void drain() {
            byte[] tmp = new byte[4096];
            try {
                int n;
                while ((n = in.read(tmp)) != -1) {
                    forward.write(tmp, 0, n);
                    forward.flush();
                    synchronized (buf) {
                        if (buf.size() < cap) {
                            buf.write(tmp, 0, Math.min(n, cap - buf.size()));
                        } else {
                            overflowed = true;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}

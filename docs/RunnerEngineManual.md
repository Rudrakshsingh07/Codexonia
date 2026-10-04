# Runner Engine Manual

This document describes the runner engine (package `runner`): how submitted
Java source is compiled, executed, and traced inside a Docker sandbox, and what
contract the emitted trace follows.

## 1. Overview

`RunnerEngine.run(CodeSubmission)` is the entry point. It:

1. Checks submission size/file-count limits (`LimitPolicy`).
2. Finds the entry point class (`MainClassDetector`).
3. Writes sources into a temporary workspace.
4. Runs the sandbox image via `DockerSandbox` (hardened flags, §4).
5. Parses the JSONL trace the sandbox produced back into `TraceEvent`s.
6. Returns a `RunOutcome`: either the full trace as versioned, gzip-ready JSON,
   or a classified failure.

The workspace is deleted after every run; nothing persists on the host.

CLI usage:

```bash
javac --add-modules jdk.jdi -d out src/main/java/runner/*.java src/main/java/runner/tracer/*.java
java --add-modules jdk.jdi -cp out runner.RunnerEngine examples/Sample.java out.json.gz
```

## 2. What the sandbox does (runner.tracer)

`TracerMain` (the image entrypoint) runs inside the container:

1. Compiles the sources with `javac -g` (`javax.tools.JavaCompiler`).
   Errors are reported as `COMPILE_ERROR` with javac diagnostics — the code is
   never modified.
2. Launches the main class under JDI (`com.sun.jdi.connect.LaunchingConnector`).
3. Enables method-entry/exit requests and a line-level step request, all
   class-filtered to the student's compiled classes so JDK internals stay out
   of the trace.
4. `TraceRecorder` turns events into JSONL:
   - `METHOD_CALL` (seq, file, class, method, line, args)
   - `ASSIGNMENT` from diffing visible local variables between line steps
     (old/new values; array values are compared by content)
   - `METHOD_RETURN` with the return value when the method returns a value.
5. Watchdog enforces wall-clock (`TIMEOUT`), stdout/stderr cap (`OUTPUT_CAP`),
   and the recorder enforces the event cap (`EVENT_CAP`); the process exits
   with a distinctive exit code for each.
6. Writes `run-result.json` (`status`, `exit_code`, capped `stdout`/`stderr`,
   `error_message`) next to `trace.jsonl` in the workspace.

`VALUE_FORMATTER` renders JDI values mechanically: primitives via `toString()`,
strings quoted and capped at 64 chars, arrays as `[a, b, ...]` (max 8 shown),
any other object as its type name. Array elements are compared by content, so
in-place writes like `nums[0] = y` surface as `nums` ASSIGNMENT events.

## 3. Trace JSON contract (schema_version: 1)

```json
{
  "schema_version": 1,
  "status": "COMPLETE | COMPILE_ERROR | TIMEOUT | OUTPUT_CAP | EVENT_CAP | RUNTIME_ERROR | INTERNAL_ERROR",
  "sources": [{"file_name": "Sample.java", "is_main_file": true}],
  "stdout": "5:5\n",
  "event_count": 12,
  "events": [
    {"seq": 1, "type": "METHOD_CALL", "file": "Sample.java", "class": "Sample",
     "method": "main", "line": 3, "args": ["[]"]},
    {"seq": 3, "type": "ASSIGNMENT", "file": "Sample.java", "class": "Sample",
     "method": "main", "line": 4, "var": "x", "new": "2"},
    {"seq": 8, "type": "METHOD_RETURN", "file": "Sample.java", "class": "Sample",
     "method": "add", "line": 12, "ret": "5"}
  ]
}
```

Rules:

- `args` elements are mechanically rendered values (see `ValueFormatter` above).
- `var`/`old`/`new`/`ret`/`args` appear only when applicable; `old` is absent
  when a variable is first seen (e.g. on method entry).
- `EventType` is forward-compatible: `COMPARISON`, `BRANCH`, `LOOP_ITERATION`,
  `CROSS_FILE_ACCESS` are reserved constants with no v1 emitters yet.

## 4. Sandbox hardening

`docker run` is invoked with: `--network none`, `--memory 512m` (configurable),
`--cpus 1`, `--pids-limit 64`, `--read-only`, tmpfs `/tmp`, `--cap-drop ALL`,
`--security-opt no-new-privileges`, `--init`, non-root `tracer` user, and a
`workspace` bind mount (SELinux-relabeled with `:Z`). The host additionally
kills the container on wall-clock timeout and reports captured output.

## 5. v1.0 known limitations

- Assignments are observed by diffing **local** variables; field mutations are not eventized (array contents are, because arrays format by value).
- Lambda bodies appear as `lambda$...` synthetic method names; `<clinit>` is filtered out.
- METHOD_RETURN omits `ret` for `void` methods.
- Method-call args are read from locals; methods with unusual bytecode (e.g. heavily inlined record accessors) may give ARGS lists inaccessible.

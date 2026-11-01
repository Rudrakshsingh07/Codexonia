# Codexonia Architecture

## Bird's Eye View

Codexonia is a web application that helps CS students understand how Java code works.
A student submits Java source in the browser; the backend compiles and executes it inside a
hardened Docker sandbox, records a structured execution trace (method calls, returns,
assignments) via the Java Debug Interface (JDI), computes a replay timeline in Java, and
returns both to the browser.

Core constraints:

- **Untrusted code execution.** Student code runs on our infrastructure, so it must be
  isolated (sandboxed), time-boxed, and resource-capped.
- **Determinism of sound.** The same event type must always produce the same tone, for
  event types never seen before, with no persisted lookup table.
- **Generate once, replay many.** One execution per submission; all playback
  (forward, backward, scrub) is done client-side against the stored trace — never by
  re-invoking the JVM.
- **No database in v1.** Trace and source are transient; no accounts, no saved sessions.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 26, Spring Boot 4 (spring-boot-starter-web), Maven |
| Instrumentation | JDI (`jdk.jdi` module) — debuggee JVM + tracer agent |
| Sandbox | Docker container (`eclipse-temurin:26-jdk`), non-root user, hardened run flags |
| Frontend | Static HTML pages (Tailwind CDN, CodeMirror 5 editor), Java-authored timeline, thin vanilla-JS renderer |
| Audio | Deterministic FNV-1a hash → tone parameter mapping (`HashFunction`), played through Web Audio |
| Tests | JUnit via spring-boot-starter-test / webmvc-test; `SelfTest` end-to-end runner check |

## Codemap

```
src/main/java/
  CodexoniaApplication.java   Spring Boot entry point; wires controllers as beans
  PageController.java         Clean URLs → static pages (/, /submit, /docs, /about,
                              /privacy, /terms)
  RunController.java          POST /api/run — accepts source/files, invokes RunnerEngine,
                              attaches the Java-authored timeline to the trace JSON
  HashController.java         GET /hash?input=... — exposes signature → hash → tone
  HashFunction.java           FNV-1a 32-bit hash of event signatures → deterministic
                              tone parameters (frequency, timbre, duration). Shared
                              contract: must match test-vectors.json exactly.
  visualizer/
    TimelineBuilder.java      Replays the trace into precomputed SceneStates: call
                              stack, variables, labels, array geometry, stdout, tones
    SceneState.java           One frame's display model (stack, variables, structures,
                              visible files, tone, animation hint)
    ToneSpec.java             Java-computed tone spec attached to each frame
    ToneProvider.java         Framework-free hook the controller wires to HashFunction
  runner/
    RunnerEngine.java         Orchestrates one job: validate → DockerSandbox → outcome
    DockerSandbox.java        Builds/runs the hardened `docker run` for a job; captures
                              stdout/stderr, enforces timeout and output caps
    CodeSubmission.java       A student's submission (one or more source files)
    JavaSourceFile.java       A single .java source file record
    MainClassDetector.java    Locates the public class with main() to use as entry point
    LimitPolicy.java          Per-job limits (timeouts, output caps) — tunables for ADR-10
    TraceEvent.java           One recorded event: type, file, class, method, line,
                              variable/old/new values, arguments
    EventType.java            v1 event taxonomy: METHOD_CALL, METHOD_RETURN, ASSIGNMENT
    TraceSerializer.java      TraceEvent list → versioned JSON (gzipped as .json.gz);
                              includes an optional error_message on classified failures
    RunOutcome.java           Classified result: success, validation error, timeout,
                              output-cap hit, runtime error — never swallowed
    Json.java                 Minimal JSON reader/writer used by the trace and
                              timeline pipelines
    SelfTest.java             End-to-end check: compiles/runs an example through Docker
    tracer/
      TracerMain.java         Runs inside the sandbox: starts debuggee JVM, sets
                              breakpoints, streams events
      TraceRecorder.java      JDI event handler — turns pauses into TraceEvents
      ValueFormatter.java     Renders variable/field values as display strings
src/main/resources/static/
  index.html                  Landing page (animated trace console)
  submit.html                 Editor + Java-authored timeline player
  docs.html                   Field guide
  about.html                  Project rationale and architecture
  privacy.html                Privacy note
  terms.html                  Terms of use
  error/404.html              Custom not-found page
  error/500.html              Custom server-error page
  favicon.ico                 Browser icon fallback
  assets/
    site.css, site.js         Shared shell, theme, nav, footer, reveal/copy behavior
    home.css, home.js         Landing layout + trace-console animation frames
    content.css               Docs/about/legal/error page typography and components
    submit.css                Submit-page timeline player styles
    tailwind.css              Precompiled Tailwind utilities (built from tailwind.config.js)
    fonts.css, fonts/         Self-hosted Inter, JetBrains Mono, and icon subset
    vendor/codemirror/        Self-hosted CodeMirror editor assets
    theme-init.js             Pre-paint theme restoration + progressive-enhancement flag
    codexonia-mark.svg        Logo / favicon source
src/test/java/
  WebLayerTest.java           Controller, route, asset, timeline, and error smoke tests
  ErrorPageIntegrationTest.java  Live-server HTML 404 and JSON error checks
  visualizer/TimelineBuilderTest.java  Frame replay, labels, layout, tones, caps
sandbox/Dockerfile            Job sandbox image (tracer classes baked in, non-root)
archive/README.md             Archived root Dockerfile (hash-vector verification image)
examples/Sample.java          Example submission used by SelfTest / manual runs
docs/                         Developer manuals (HashFunctionManual.md,
                              RunnerEngineManual.md, JavaVisualizerPlan.md)
Project Specification/        PRD.md, Codexonia-SRS.md, Codexonia-ADR.md (decision log)
test-vectors.json             Signature → hash → tone vectors (cross-language contract)
Diagrams/                     UML, sequence, use-case, dataflow diagrams
Stitch/                       Original UI mockups (source for static pages)
Study/, .obsidian/            Personal notes vault (git-ignored study material)
run.sh                        One-shot launcher: sandbox image + Spring Boot + browser
```

## Architectural Layers and Boundaries

```
Browser (landing / editor + timeline player / field guide / legal pages)
        │  POST /api/run ──► trace JSON + additive timeline sidecar
        │  HTML pages ─────► shared shell (site.css/site.js + page CSS)
Spring Boot controllers (PageController, RunController, HashController)
        │  CodeSubmission in, RunOutcome/traceJson out
        │  TimelineBuilder(trace, ToneProvider) ──► frames + tone specs
RunnerEngine + DockerSandbox (host side)
        │  hardened `docker run`, stdio pipes, timeout/cap enforcement
Sandbox container: TracerMain ──JDI──► debuggee JVM (student code)
        │  TraceEvent stream
TraceSerializer ──► versioned JSON trace (gzipped) ──► TimelineBuilder ──► browser
```

Rules:

1. **The web layer never touches Docker or JDI directly.** It goes through
   `RunnerEngine`/`DockerSandbox`; tracing internals live only in `runner.tracer`,
   which executes inside the sandbox, not in the server process.
2. **The debuggee JVM never sees credentials, the network, or the host filesystem.**
   Only the sandbox boundary may be crossed, and results exit as JSON over stdio.
3. **HashFunction is a pure, self-contained module.** No Spring, no I/O beyond reading
   its input argument; both `HashController` and the CLI use the same code path.
4. **The static site has one shell.** Header, footer, theme, mobile navigation, and
   reveal behavior live in `assets/site.js`/`site.css`; page-specific styles stay in
   their own files. `PageController` only forwards clean URLs to static HTML, so the
   pages stay framework-free and can be served by any static host.

## Web Surface

The site is static HTML with a small amount of vanilla JavaScript. There is no
template engine and no server-side rendering step.

- **Clean URLs:** `PageController` forwards `/`, `/submit`, `/docs`, `/about`,
  `/privacy`, and `/terms` to the corresponding static files. `/home` redirects
  to `/`. Trailing slashes are accepted for the named pages.
- **Shared shell:** every page mounts `#site-header` and `#site-footer`; `site.js`
  renders the navigation, theme toggle, mobile panel, current-page state, and
  copyright year. Theme preference is stored in `localStorage` and restored before
  paint by `theme-init.js`.
- **Landing animation:** `home.js` plays a fixed, pre-recorded trace frame sequence
  (calls, assignments, returns) to demonstrate the evidence model. It is decorative;
  it does not execute Java.
- **Submit flow:** `submit.html` posts to `POST /api/run`, then renders the returned
  trace and its additive `timeline`. The player shows the active source line, call
  stack, variables, Java-laid-out arrays, stdout, and the ordered event stream, with
  play/pause, stepping, scrubbing, speed, and a Web Audio toggle. Compile and
  classification errors are displayed in the player and a dedicated panel, and the
  source buffer is retained.
- **Error pages:** Spring Boot's error dispatch resolves `static/error/404.html` and
  `static/error/500.html` for HTML requests. API requests keep the JSON error shape.
- **Progressive enhancement:** reveal animations are visible without JavaScript
  (`html:not(.js)` fallback), and `prefers-reduced-motion` disables the landing
  animation and scroll reveals.

The timeline is computed entirely in Java before the response leaves the server.
`TimelineBuilder` replays the trace into frames containing the active file and line,
call stack, visible variables, array geometry, stdout visibility, labels, tone specs,
and animation hints. The browser renderer paints those fields and owns only the wall
clock, cursor, layout transitions, and Web Audio scheduling; it never reconstructs
scene state from raw events. The trace stays unchanged and self-contained, so older
clients can ignore the additive `timeline` field.

## Key Architectural Decisions

Recorded in full in `Project Specification/Codexonia-ADR.md`. The invariants that must not
be broken:

- **ADR-1/7 — JDI inside a hardened sandbox.** No in-JVM restrictions (SecurityManager is
  rejected); isolation is enforced by Docker flags (no network, read-only rootfs, dropped
  capabilities, non-root user, CPU/memory/pids limits) plus an external wall-clock kill.
- **ADR-2 — Explicit 32-bit hash for tones.** Never use `String.hashCode()` or any
  language built-in for tone mapping; all implementations must match
  `test-vectors.json` byte-for-byte.
- **ADR-3 — Step-backward by state re-rendering.** Playback never re-runs the program.
  Traces are capped so full snapshots fit memory/transfer budgets; the schema must stay
  able to express periodic keyframes + diffs.
- **ADR-4 — No database.** Client holds the trace in memory; the server persists
  nothing and keeps no result cache in v1. No persistence of student code or traces.
- **ADR-5 — Visualization and audio share the same ordered frame timeline.** Both
  read the Java-computed frames and tone specs; the browser cursor keeps them
  synchronized. Neither calls the other.
- **ADR-8 — The trace schema is the Java/browser boundary.** It is versioned
  (`schema_version`); any change requires a version bump and re-freezing both sides.
  The `timeline` sidecar is additive and separately versioned.
- **ADR-10 — Every limit hit is a classified outcome**, surfaced to the student as a
  clear message (validation error, timeout, output cap, runtime error).

## Data Flow & Security

**Run flow:**

1. Browser posts `{source}` or `{files[]}` to `POST /api/run`.
2. `RunController` turns the request into a `CodeSubmission` (sniffing the public
   class name for single-`source` requests); `RunnerEngine` validates it and detects
   the main class.
3. `RunnerEngine` hands it to `DockerSandbox`, which starts the sandbox container
   (`codexonia-sandbox:latest`) with hardened flags and a wall-clock timeout.
4. Inside the sandbox, `TracerMain` compiles the source, launches the debuggee JVM under
   JDI, records `METHOD_CALL` / `ASSIGNMENT` / `METHOD_RETURN` events, and prints the
   serialized trace to stdout.
5. `DockerSandbox` captures output (capped), classifies the outcome, destroys the
   container, and returns a `RunOutcome`.
6. The controller responds with the JSON trace with an additive `timeline` sidecar,
   or a structured error. Classified failures include `error_message` in both the
   trace and timeline; the fallback response always has a JSON body.
7. The Submit page paints the timeline: active line, call stack, variables, arrays,
   stdout, event list, and tone specs. Playback moves a browser cursor through the
   precomputed frames; the JVM is never re-invoked.

**Security rules:**

- Sandbox containers run with **no network**, read-only rootfs, all capabilities dropped,
  non-root user, and cgroup resource limits.
- The host JVM (Spring Boot) never executes student code in-process.
- Submitted source is not logged by the server and is not persisted past the job.
- Output size, run time, and trace size are bounded by `LimitPolicy`; exceeding them
  produces an explicit error, not a hung server.

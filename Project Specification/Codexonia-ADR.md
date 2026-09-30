# Codexonia

## Architecture Decision Records (ADR Log)

*Version 2.0 (Web edition) | Planning Deliverable | Team: Dev A / Dev B / Dev C*

---

# Purpose of This Document

This log records the significant architectural decisions made during the planning phase of Codexonia, why they were made, what alternatives were rejected, and what trade-offs each decision carries. Each entry follows the standard ADR format (Context → Decision → Alternatives → Consequences) so that decisions remain traceable and reversible if new information emerges during implementation.

**Change note (v1.0 desktop → v2.0 web):** Delivery moves from JavaFX desktop to the web. ADR-6 is superseded. ADR-1 to ADR-4 are amended. ADR-5 is unchanged in substance. ADR-7 to ADR-10 are new and carry status **Proposed**, meaning the team should confirm or change them.

# ADR Index

- ADR-1: Execution Instrumentation Strategy (JDI), *amended: runs inside a sandbox*
- ADR-2: Deterministic Tone Generation via Hash Function, *amended: implemented in TypeScript, explicit 32-bit hash*
- ADR-3: Step-Backward Playback via Recorded State Snapshots, *amended: snapshot size under network transfer*
- ADR-4: In-Memory Trace Event Store, *amended: client memory plus short-TTL server cache*
- ADR-5: Decoupling Visualization and Audio as Independent Consumers of the Trace Stream
- ADR-6: Web Frontend Stack (TypeScript), **supersedes "JavaFX as the UI Framework"**
- ADR-7: Sandbox Strategy for Untrusted Code **[New]**
- ADR-8: Client-Server API and Trace Format **[New]**
- ADR-9: Hosting and Deployment **[New]**
- ADR-10: Abuse Prevention, Limits and Rate Limiting **[New]**

---

## ADR-1: Execution Instrumentation Strategy

**Status: Accepted (amended for web)**      Owner: Dev A

**Context**

- Codexonia must capture a complete, ordered trace of runtime events (assignments, comparisons, branches, loop iterations, calls/returns, cross-file access) from arbitrary but working student-supplied Java code.
- The chosen mechanism directly determines what level of detail (FR6) is even obtainable, and is the single highest-risk technical decision in the project.
- **[WEB]** The code now runs on a server we operate, so the debuggee must run under strict isolation (see ADR-7).

**Decision**

- Use the Java Debug Interface (JDI) to run the student's code under a debug-mode JVM, set step/line/method-entry breakpoints programmatically, and read local variable and field state at each pause.
- **[WEB]** The JDI debugger process and the debuggee JVM both run **inside the sandbox** for a single job, and the sandbox is destroyed when the job ends.
- JDI is chosen over lower-level bytecode rewriting because it requires no modification of the student's .class files and works with standard javac output, which keeps FR4 (accept any code that compiles) simple to satisfy.

**Alternatives Considered**

- Java Instrumentation API (javaagent + ASM bytecode weaving): rejected for v1. It gives finer control and better performance, but requires rewriting bytecode to inject probes, which is significantly more complex to get correct for arbitrary/unseen student code within our timeline.
- Manual AST-level source instrumentation (inject logging statements into a copy of the source before compiling): rejected. Fragile against complex expressions, harder to guarantee it doesn't change program semantics or introduce compile errors of our own making.
- Full custom Java interpreter: rejected. Effectively reimplementing the JVM; far outside v1 scope and risk budget.
- **[WEB]** In-browser Java runtimes (e.g., CheerpJ, TeaVM): rejected. They do not provide JDI, so the trace mechanism would have to be rebuilt.

**Consequences**

- JDI trace capture is slower than an instrumented agent, which may limit responsiveness on very long traces. On the web this shows up as job latency and worker occupancy, and is bounded by the limits in ADR-10.
- **[WEB]** The debuggee process is isolated from the tracer, and the sandbox isolates both from the server. A runaway or malicious program is contained by the sandbox rather than by process separation alone.
- This decision is the direct input to TraceRecorder's design and should be validated with a small proof-of-concept before Dev A commits further implementation time. **[WEB]** The proof of concept should run inside the chosen sandbox (ADR-7) to confirm JDI works there and to measure per-job startup time.

---

## ADR-2: Deterministic Tone Generation via Hash Function

**Status: Accepted (amended for web)**      Owner: Dev C

**Context**

- FR12 to FR14 require that every event type produces a tone, that the same event type always produces the same tone, and that this holds even for event types never seen before in the session, with no persisted lookup table (Section 8 of the PRD).
- **[WEB]** Tone generation now runs in the browser in TypeScript. Java's `String.hashCode()` is not available, and JavaScript numbers are 64-bit doubles rather than 32-bit ints.

**Decision**

- Define a canonical 'event signature' string per event (e.g., event type + normalized key attributes such as loop-type or comparison-operator, deliberately excluding volatile values like specific variable contents so the same *kind* of event always maps identically).
- Feed the signature through an **explicitly specified 32-bit hash** (Proposed: FNV-1a, implemented with `Math.imul` and `>>> 0`, over the signature's UTF-8 or UTF-16 code units as fixed in the spec), then map hash output ranges deterministically to frequency, timbre, and duration parameters in ToneGenerator.
- Publish **shared test vectors** (signature → expected hash and tone parameters). Any implementation, including a Java prototype, must match them exactly.
- Per-signature memoization in the browser is allowed, since it caches a pure function and is not persisted storage.

**Alternatives Considered**

- Pre-authored sound library with manual event-to-sound mapping: rejected. Explicitly disallowed by FR13, and does not generalize to unseen event types.
- Persisted mapping table generated on first encounter and looked up thereafter: rejected. Satisfies 'same sound every time' but violates the 'no persistent sound-mapping storage' constraint and adds unnecessary state to manage.
- **[WEB]** Relying on a language built-in string hash: rejected. Not guaranteed identical across languages or engine versions, which would break the Determinism NFR.
- **[WEB]** Computing tones on the server and shipping them in the trace: rejected for v1. It couples audio design to the backend and inflates the trace.

**Consequences**

- Sound quality is a function of the hash-to-parameter mapping design; this needs its own small design pass (see the Tone Hash Function design doc) but is decoupled from correctness.
- Because the tone is purely computed, ToneGenerator can be unit-tested by asserting the same signature always returns identical output, using the shared test vectors. This is an easy, high-confidence test for Dev C to write early.
- Hashing cost is negligible in the browser; the real timing risk is audio scheduling, handled in ADR-6 (audio context clock).

---

## ADR-3: Step-Backward Playback via Recorded State Snapshots

**Status: Accepted (amended for web)**      Owner: Dev A / Dev B (shared)

**Context**

- FR17 requires exact single-event step-backward. Java program execution is not natively reversible, so 'going back' must be simulated.
- This decision was flagged as an explicit open item in the PRD and needed to be resolved before Dev B could design VisualizationController and Dev A could finalize TraceEvent's shape.
- **[WEB]** Snapshots now travel over the network and live in browser memory, so trace size is a hard constraint (FR20).

**Decision**

- Each TraceEvent carries enough state (old_value/new_value, and an associated DataStructureState snapshot where relevant) that stepping backward means re-rendering the previously recorded state at index N-1, rather than re-running the program.
- The full trace is generated once, up front, during Generate Execution Trace (Use Case 4). Execution happens exactly once per submission, and all playback (forward, backward, jump-to) is done in the browser by replaying stored events, not by re-invoking the JVM.
- **[WEB]** Snapshot encoding: start with **full snapshots per relevant event** for simplicity, but keep the Trace Schema able to express **periodic keyframes plus diffs** so the encoding can change without changing the playback model. Switch to keyframes plus diffs if measured traces for typical programs approach the size cap.
- **[WEB]** The trace is compressed in transit (ADR-8) and capped (ADR-10).

**Alternatives Considered**

- True reverse execution of the JVM: rejected. Not practically achievable within scope.
- Re-running the program from the start up to event N-1 on every step-backward: rejected. Correct but potentially slow and wasteful; on the web it would also mean a server round trip and a sandbox run per click.
- **[WEB]** Diffs only, with no snapshots: deferred. Smaller traces, but every backward step requires reconstruction from the previous keyframe, which adds client complexity.

**Consequences**

- The full trace must be captured and buffered on the server, then downloaded and held in browser memory before playback begins, which caps practical program complexity/length for v1 (acceptable given v1 targets student-scale solutions, not large systems).
- Playback controls never wait on the network after the trace is loaded, which keeps step and scrub responsive.
- The full-snapshots-vs-diffs question is **partly resolved (full first, revisit with data)** rather than closed.

---

## ADR-4: Trace Event Store

**Status: Accepted (amended for web)**      Owner: Dev C

**Context**

- PRD Section 13 states no accounts or cross-session history are required for v1.
- **[WEB]** The trace is produced on the server but consumed in the browser, so there are now two short-lived homes for it.

**Decision**

- **Client:** TraceEvent, DataStructureState, and PlaybackState are held as in-memory TypeScript objects for the duration of one session and discarded on page close or new submission.
- **Server:** the finished trace is held in a **short-TTL job result cache** (in memory or temp storage) only long enough for the client to download it. Source files and traces are deleted after the retention period (value TBD).
- No database in v1.

**Alternatives Considered**

- Embedded or hosted database (SQLite/H2/Postgres): rejected for v1. Adds setup and schema-migration overhead with no corresponding requirement; reconsider if a later version adds accounts or saved sessions.
- **[WEB]** Persisting traces so users can share links: rejected for v1. It implies storing user code, which raises privacy and abuse questions that are out of scope.

**Consequences**

- The v1 data layer stays simple: plain model classes and a TTL cache, no ORM or DB dependency.
- PlaybackState is confirmed as transient client-side UI state only. It appears in the UML class diagram but not in the ERD as a persisted entity.
- **[WEB]** Link sharing and saved sessions are explicitly deferred; a future ADR would need to cover storage, privacy, and retention.

---

## ADR-5: Decoupling Visualization and Audio as Independent Trace Consumers

**Status: Accepted (unchanged in substance)**      Owner: Dev A (contract owner), Dev B, Dev C (consumers)

**Context**

- Section 11 of the PRD specifies VisualizationController and AudioPlayer should both consume the same TraceEvent stream without depending on each other.

**Decision**

- Both components subscribe to the same ordered TraceEvent sequence exposed by the client-side Trace Event Store, driven by a single shared PlaybackState (current index, speed, playing flag) owned by PlaybackController.
- Neither component calls into the other directly; synchronization happens only through the shared playback position, following an observer-style pattern. **[WEB]** This can be implemented with a small shared store or event emitter in the frontend.

**Alternatives Considered**

- Direct coupling (VisualizationController invokes AudioPlayer per event): rejected. It would force Dev B and Dev C's modules to share an interface contract beyond just the trace format, increasing integration risk given they're built by different developers in parallel.

**Consequences**

- Lets Dev B and Dev C build and test their modules independently against a shared TraceEvent/PlaybackState contract, which is the main integration point that must be finalized and frozen early. **[WEB]** Both developers now work in TypeScript, so the shared types are also directly importable.

---

## ADR-6: Web Frontend Stack (supersedes "JavaFX as the UI Framework")

**Status: Proposed**      Owner: Dev B / Dev C

**Context**

- The product is now delivered in the browser (PRD Section 1). The earlier decision to use JavaFX, and its rejection of web delivery, no longer applies.
- The frontend must provide a code editor, multi-file panels with connector lines, an animation canvas, a playback bar, and tone playback.

**Decision**

- **Language and framework:** TypeScript with a component framework (Proposed: React, or Svelte; choose whichever the team is more comfortable with) and a build tool such as Vite.
- **Editor:** Monaco or CodeMirror for syntax highlighting and editing.
- **Visualization:** SVG (with optional D3 helpers) for data structures and connector lines; fall back to Canvas only if performance requires it.
- **Audio:** the Web Audio API directly (oscillators and gain envelopes), with Tone.js as an optional convenience layer. Tones are scheduled on the **AudioContext clock**, not `setTimeout`, and the context is resumed on the first user gesture (FR22).
- **State:** a small client store holding the trace and PlaybackState, consumed by both visualization and audio (ADR-5).

**Alternatives Considered**

- JavaFX (previous decision): superseded. Good fit for a desktop-only product, but it rules out link-and-go access, and audio and editor tooling are weaker than the web's.
- Java-on-the-web frameworks (e.g., Vaadin, GWT/TeaVM-based UI): rejected. They keep everything in Java but fit animated, canvas-style UI and Web Audio poorly.
- Electron: rejected. It is a desktop install again and gives up the main benefit of web delivery.

**Consequences**

- Dev B and Dev C work in TypeScript while Dev A works in Java; the Trace Schema (ADR-8) is the language boundary.
- Editor, animation, and audio tooling are richer and cheaper than in JavaFX, reducing custom code.
- The team must handle browser differences (autoplay policy, Safari audio quirks) and test across the supported browsers.

---

## ADR-7: Sandbox Strategy for Untrusted Code **[New]**

**Status: Proposed**      Owner: Dev A

**Context**

- On the web, student-submitted Java runs on a server we operate, so arbitrary untrusted code is executed remotely. Without strong isolation, one submission could read secrets, attack the network, exhaust the host, or persist between users.

**Decision**

- Run each job in a **fresh, disposable, isolated environment** that is destroyed at job end. Proposed baseline: a **Docker container with a hardened profile** (non-root user, read-only root filesystem with a small tmpfs workspace, all Linux capabilities dropped, seccomp profile, no network, cgroup limits on CPU, memory, and process count), run under **gVisor (runsc)** for a stronger kernel boundary.
- Enforce **wall-clock timeout** outside the sandbox (the controller kills the container), not only inside it.
- Cap captured stdout/stderr and workspace disk usage.
- Run the sandbox on dedicated worker hosts with no access to secrets or internal services.

**Alternatives Considered**

- Plain Docker with default settings: rejected. Shared kernel with default permissions is too weak for running arbitrary code.
- Firecracker microVMs: strongest isolation and a good upgrade path, but more operational complexity and hosting constraints; revisit if the service becomes public at scale.
- Java SecurityManager / in-JVM restrictions: rejected. Deprecated for removal and not a sound boundary for hostile code.
- Third-party code-execution APIs: a viable shortcut, but they may not support JDI and raise cost and data-handling questions; evaluate only if self-hosting proves too heavy.

**Consequences**

- Adds infrastructure work and the highest security responsibility in the project; it should be proven early with the JDI proof of concept (ADR-1).
- Per-job startup time adds latency; a pool of pre-warmed, unused sandboxes may be needed.
- Limits and failure modes from this ADR surface to the student as clear error messages (Use Case 9).

---

## ADR-8: Client-Server API and Trace Format **[New]**

**Status: Proposed**      Owner: Dev A (schema), Dev C (API)

**Context**

- The trace is generated on the server and consumed in the browser, so the two sides need a stable, language-neutral contract and a job lifecycle.

**Decision**

- **Job API (REST, described in OpenAPI):** `POST /jobs` (submit files and optional stdin) → `202` with job id; `GET /jobs/{id}` (status: queued, compiling, running, complete, failed, cancelled, plus error details); `DELETE /jobs/{id}` (cancel); `GET /jobs/{id}/trace` (download the trace). Status updates via polling first; SSE or WebSocket can be added later.
- **Trace format:** a **versioned JSON schema** (`schema_version` field) containing the source file list, the ordered event list, and DataStructureState snapshots. Served with gzip or Brotli compression.
- **Delivery mode for v1:** **single batch download** after the job completes. This fits the "generate once, replay many" model (ADR-3).
- Generate Java classes and TypeScript types from the schema (or validate both against it) so they cannot drift. The client validates the trace on load.

**Alternatives Considered**

- Streaming events to the browser as they are produced: deferred. It lets playback start earlier, but complicates step-backward and rewind, and gains little for student-scale traces.
- Binary formats (Protobuf, MessagePack): deferred. Smaller and faster to parse, but harder to debug; compressed JSON is sufficient until measurements say otherwise.
- Keeping a Java-only contract (as in the desktop design): not possible across the Java/TypeScript boundary.

**Consequences**

- The schema is the main integration point and must be frozen early; changes require a `schema_version` bump.
- Large traces increase download and parse time; limits in ADR-10 bound this.
- Batch delivery means the student waits for the whole job before playback, so progress status must be clear (FR19).

---

## ADR-9: Hosting and Deployment **[New]**

**Status: Proposed**      Owner: Dev C

**Context**

- A web app needs a place to run the frontend, the job API, and the sandbox workers.

**Decision**

- **Frontend:** static hosting with a CDN.
- **Backend:** a containerized Java service (Proposed: Spring Boot or Javalin) for the job API, plus worker nodes that run sandboxes (ADR-7), connected by a simple job queue. Start with a single VM running both, then separate workers if load requires.
- **Environments:** local (Docker Compose), plus one deployed environment.
- Host choice is left open (see PRD open items); cost and sandbox support (gVisor or nested virtualization) are the selection criteria.

**Alternatives Considered**

- Serverless functions for execution: rejected. JDI and a long-lived debuggee process fit poorly with short, limited serverless runtimes.
- Kubernetes from the start: rejected for v1. Operational overhead outweighs the benefit at student-project scale.

**Consequences**

- Introduces running costs and operations work that the desktop design avoided.
- Keeping the API stateless (results in a TTL cache) leaves scaling out open later.

---

## ADR-10: Abuse Prevention, Limits and Rate Limiting **[New]**

**Status: Proposed**      Owner: Dev C (enforcement), Dev A (sandbox limits)

**Context**

- An anonymous public endpoint that runs arbitrary code is a target for abuse (crypto mining, denial of service, data exfiltration attempts).

**Decision**

- **Per-job limits** (values set after the proof of concept): CPU time, wall-clock timeout, memory, process count, stdout/stderr size, source size and file count, **maximum trace events and bytes** (FR20).
- **Per-client limits:** submissions per minute and concurrent jobs per anonymous client (IP/session based), plus a global queue depth limit with a clear "busy" response.
- **No network** from the sandbox (ADR-7); input size validated at the API before any job is created.
- **Retention:** delete source and traces after a short TTL; do not log submitted source in general-purpose logs.
- Add a CAPTCHA or simple token gate only if abuse actually appears.

**Alternatives Considered**

- Requiring accounts before use: rejected for v1. It adds friction and scope (auth, storage, privacy) for limited benefit at this scale.
- No rate limiting, relying on sandbox limits alone: rejected. Sandbox limits protect the host from one job, not from a flood of jobs.

**Consequences**

- Legitimate heavy programs (large loops, big inputs) may be rejected; the error message must say which limit was hit.
- Limits become tunable configuration and should be revisited with real usage data.

---

# Summary Table

| **ADR** | **Decision** | **Status** | **Primary Owner** |
| --- | --- | --- | --- |
| 1 | Execution instrumentation via JDI, inside a sandbox | Accepted (amended) | Dev A |
| 2 | Hash-based deterministic tone generation, explicit 32-bit hash in TypeScript | Accepted (amended) | Dev C |
| 3 | Step-backward via stored snapshots; full snapshots first, keyframes plus diffs if needed | Accepted (amended) | Dev A / Dev B |
| 4 | Client in-memory trace store plus short-TTL server cache, no DB | Accepted (amended) | Dev C |
| 5 | Decoupled visualization/audio via shared trace stream | Accepted | Dev A / All |
| 6 | TypeScript web frontend (supersedes JavaFX) | Proposed | Dev B / Dev C |
| 7 | Hardened container under gVisor for untrusted code | Proposed | Dev A |
| 8 | REST job API, versioned compressed JSON trace, batch delivery | Proposed | Dev A / Dev C |
| 9 | Static frontend plus containerized Java backend and workers | Proposed | Dev C |
| 10 | Per-job and per-client limits, rate limiting, short retention | Proposed | Dev C / Dev A |

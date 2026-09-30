# Product Requirements Document (PRD)
## Project Name: Codexonia
### Version: 2.0 (Web edition; v1 scope only, unless marked "Future/v2")

> **Change note (v1.0 desktop → v2.0 web):** Delivery changes from a JavaFX desktop app to a browser-based web app. Trace generation moves to a sandboxed server; visualization, audio, and playback run in the browser as a pure replay of a JSON trace. New or changed items are marked **[WEB]**.

---

## 1. Overview

Codexonia is a **web application** (Java backend for compilation, sandboxed execution, and tracing; TypeScript frontend for editing, visualization, audio, and playback) that helps Computer Science students understand **working, error-free Java code** written by someone else (a LeetCode solution, a senior's code, a teacher's example, etc.) by generating a **synchronized visual animation of the code's data structures** combined with **deterministic audio cues**, tied to the code's actual execution trace.

The core problem Codexonia solves: a student can read code and see that it *works*, but still cannot mentally trace **why control flows the way it does** (branches, loops, method calls) or **why data changes the way it does** (variable/value mutations, and exactly which line caused them). Codexonia makes both of these observable, visually and audibly, instead of requiring the student to trace it by hand.

---

## 2. Goals & Non-Goals

### 2.1 Goals (v1)
- Accept working, multi-file Java source code (via in-browser editor or file upload).
- **[WEB]** Execute the code safely on a server inside a sandbox (untrusted code), and trace it.
- Capture every relevant runtime event (assignment, comparison, branch decision, loop iteration, method call/return, cross-file data access).
- Render an animated, fully **mechanically labeled** visual representation of data structures as they change.
- Generate a **deterministic, algorithmically derived audio tone** for every distinct event type, with no manually pre-mapped sound library and no persistent sound-mapping storage.
- Provide **video-player-style playback controls**, plus single-step forward/backward execution control.
- Detect and report compile/runtime errors without attempting to fix them.
- Visually represent multi-file execution: when execution or data pulls come from a file other than the main file, show the relevant files simultaneously and draw an explicit connection (e.g., a line to a "data source" block) at the moment the cross-file access occurs.

### 2.2 Non-Goals (explicitly out of scope for v1)
- Auto-correcting or suggesting fixes for broken code (planned for v2).
- Semantic/interpretive labeling (e.g., "this is the partition step," "this is the base case"). v1 is mechanical/literal labeling only (v2 feature).
- Support for languages other than Java.
- Explaining *why an algorithmic approach works* at a conceptual level (e.g., "why two-pointer works here"). Codexonia addresses control/data flow, not algorithmic insight.
- **[WEB]** Offline use.
- **[WEB]** Native mobile apps and small-screen layouts (v1 targets desktop browsers; tablets are best-effort).
- **[WEB]** User accounts and saved history (see Section 13).
- **[WEB]** Programs that require network access, interactive long-running input, GUIs, or multithreading-heavy behavior (see FR18).

*(Removed from v1.0: "Web-based delivery: v1 is a JavaFX desktop application only.")*

---

## 3. Target Users & Personas

**Primary user: CS Student**
- Has found or been given working Java code they did not write.
- Understands basic programming concepts but cannot mentally simulate execution of the specific code in front of them.
- Wants to *see* and *hear* what the code does, step by step, at their own pace.
- **[WEB]** Expects to open a link and start, with no JDK or installer.

**Example scenarios:**
1. A student solves a LeetCode problem incorrectly, looks up the accepted solution, but still can't understand its logic. They paste the solution into Codexonia.
2. A student receives multi-file example code from a senior or instructor illustrating a topic (e.g., OOP, recursion, data structures) and cannot trace how data or control moves across files.

---

## 4. User Roles / Actors (for Use Case Diagram)

| Actor | Description |
|---|---|
| **Student (Primary Actor)** | Uploads/edits code, controls playback, views/listens to the visualization. |
| **Codexonia System** | The application itself: web client plus backend services. Validates, traces, visualizes, and sonifies code. |
| **Sandboxed Execution Service (Secondary/Supporting Actor)** | **[WEB]** Isolated environment (JDK + debuggee JVM) that compiles and executes submitted code under resource limits. Replaces the "Java Compiler/Runtime" actor. |
| **Operator (Optional)** | **[WEB]** Person who deploys and monitors the service, and adjusts limits. Include only if the system is run publicly. |

*(Note: There is a single human end-user role, the Student. There is no teacher/admin role in v1.)*

---

## 5. Use Cases

1. **Submit Code via Text Editor**
   Student types or pastes Java code into the in-browser editor (with syntax highlighting and editing support).

2. **Submit Code via File Upload**
   Student uploads or drags and drops one or more `.java` files (multi-file project support), subject to size limits.

3. **Validate Code** **[WEB]**
   The client sends the submission to the server, creating a job. The server compiles/checks the code. If errors exist, the system returns them to the student and halts (no auto-fix, no visualization generated).

4. **Generate Execution Trace** **[WEB]**
   The server runs the validated code in the sandbox and records a structured sequence of runtime events. The job is asynchronous: the client shows progress, then receives the completed trace as JSON.

5. **View Visualization**
   Student watches an animated, labeled representation of data structures and control flow in the browser, synchronized with generated audio cues.

6. **Control Playback**
   Student uses play, pause, speed up, slow down, fast-forward, rewind, step-forward (single execution step), and step-backward (single execution step) controls. All of this runs client-side against the downloaded trace.

7. **Observe Cross-File Execution**
   When execution or data access crosses into another file, the system displays the relevant files simultaneously and visually indicates the connection at the moment it occurs.

8. **Hear Audio Cue for Event**
   For every event in the trace, the client plays a tone. The same event type always yields the same tone, computed deterministically (see Section 8). **[WEB]** The first Play click unlocks the browser's audio context.

9. **Handle Execution Limit Exceeded** **[WEB]**
   If the program exceeds the time, memory, output, or trace-size limit, or hits the rate limit, the system stops the job and tells the student which limit was hit and what to try (e.g., a smaller input).

---

## 6. Functional Requirements

### 6.1 Input
- FR1: The system shall accept Java source code via an in-browser text editor.
- FR2: The editor shall provide syntax highlighting and allow direct editing (Proposed: Monaco or CodeMirror).
- FR3: The system shall accept one or more `.java` files via browser file upload or drag-and-drop (multi-file projects), with per-file and total size limits.
- FR4: The system shall only accept code that compiles and runs with zero errors; if errors are present, the system shall display the error(s) to the student without modifying the code.

### 6.2 Execution & Tracing
- FR5: **[WEB]** The system shall compile and execute submitted Java code on the server inside a sandbox with CPU, memory, and wall-clock limits, no network access, and an isolated, ephemeral filesystem.
- FR6: The system shall capture a structured, ordered trace of execution events, at minimum including:
  - Variable declaration/assignment (with old value, new value, line number, file).
  - Conditional evaluation (condition text, result, line number, file).
  - Branch entry (which branch of an if/else/switch was taken).
  - Loop iteration start/end (loop type, iteration count, line number, file).
  - Method call (caller file/class/method, callee file/class/method, arguments, line number).
  - Method return (return value, line number).
  - Cross-file data or method access (source file/class, destination file/class, what was accessed).
- FR7: Each trace event shall record enough metadata to support both the visual animation and the audio cue generation.
- FR18: **[WEB]** The system shall enforce and report limits: execution timeout, memory cap, captured stdout/stderr cap, and no network. Programs needing stdin shall be handled per a defined policy (Proposed: a single optional stdin text box, consumed at run start).
- FR19: **[WEB]** Validation and tracing shall run as asynchronous jobs with visible status (queued, compiling, running, complete, failed), and the student shall be able to cancel a running job.
- FR20: **[WEB]** The system shall enforce a maximum trace size (event count and serialized bytes). If exceeded, the job fails with a clear message. Traces shall be delivered compressed.
- FR21: **[WEB]** The system shall rate-limit submissions per client (anonymous, e.g., per IP/session) to prevent abuse.

### 6.3 Visualization
- FR8: The system shall animate data structures (e.g., arrays, and other structures as needed) reflecting their state changes over time, in sync with the trace.
- FR9: All visual elements shall be **mechanically labeled**: labeled using the code's own literal content (variable names, line numbers, condition text, method names) with nothing left for the student to infer or guess.
- FR10: When execution enters a method defined in a file other than the currently focused file, the system shall display the relevant files simultaneously (not merely switch views).
- FR11: When data is accessed from another file, the system shall draw a visible connecting line from the accessing block to the source block, appearing at the moment the access occurs.

### 6.4 Audio
- FR12: The system shall generate an audio tone for every trace event.
- FR13: The tone for a given event type shall be produced via a deterministic algorithm (e.g., a hash/mapping function from event-type signature to frequency/timbre parameters), not via a stored/retrieved lookup table.
- FR14: The same event type shall always produce the same tone, including event types encountered for the first time during a given session (the algorithm must generalize to unseen event types without prior storage). Output shall be identical across browsers and sessions.
- FR22: **[WEB]** The client shall start audio only after a user gesture (browser autoplay policy), and shall schedule tones using the audio context clock so sound stays in sync with visuals.

### 6.5 Playback Controls
- FR15: The system shall provide Play, Pause, Fast-Forward, Rewind, and Speed adjustment controls, consistent with standard video-player conventions.
- FR16: The system shall provide a Step-Forward control that advances the visualization by exactly one execution event.
- FR17: The system shall provide a Step-Backward control that reverses the visualization by exactly one execution event.

---

## 7. Data Flow Overview (for Data Flow Diagram)

**External Entity:** Student (browser)

**High-level process flow:**

1. **Student** → (Java source code, via editor or file upload) → **Input Handling Process** *(browser)*
2. **Input Handling Process** → (submission request over HTTPS) → **Job API** *(server)* **[WEB]**
3. **Job API** → (source files) → **Compilation & Validation Process** *(sandbox)*
   - If invalid → (error report) → **Job API** → **Student**
   - If valid → proceeds
4. **Compilation & Validation Process** → (compiled/executable code) → **Execution & Tracing Process** *(sandbox)*
5. **Execution & Tracing Process** → (ordered stream of Trace Events) → **Trace Serializer** → (JSON trace, compressed) → **Job API** **[WEB]**
6. **Job API** → (trace download) → **Trace Event Store** *(in-memory in the browser)* **[WEB]**
7. **Trace Event Store** → (event stream) → **Visualization Rendering Process** *(browser)*
8. **Trace Event Store** → (event stream) → **Audio Generation Process** *(browser, Web Audio)*
9. **Visualization Rendering Process** → (animated frames) → **Student**
10. **Audio Generation Process** → (generated tone/audio signal) → **Student**
11. **Student** → (playback control commands) → **Playback Controller Process** *(browser)*
12. **Playback Controller Process** → (position/speed state) → **Trace Event Store** (controls which events are currently being rendered/played)

**Network boundary [WEB]:** Steps 2 and 6 cross the network. Everything from step 7 onward is client-only and works without further server calls.

**Key data stores:**
- Source Code Files (input, multi-file; ephemeral on the server, deleted after the job completes or expires)
- Job Result Cache (server, short TTL, holds the finished trace for download) **[WEB]**
- Trace Event Store (in browser memory; sequential events with metadata: type, line, file, variable/values, call context)
- Tone-Generation Function output is computed on demand and **not persisted** (per FR13/FR14).

---

## 8. Sound Generation Model

- Each **event type** (a category, e.g., "variable assignment," "loop iteration," "comparison," "method call," "cross-file access," etc.) is mapped to a set of identifying characteristics (an "event signature").
- A **deterministic function** (e.g., a hash function feeding into a frequency/timbre-selection formula) takes the event signature as input and produces the same audio output (frequency, and optionally instrument/timbre/duration) every time it is given that same signature, whether or not the signature was seen before in this session.
- This removes the need for a stored, retrievable sound-mapping table/database; the "memory" of which sound belongs to which event is implicit in the deterministic algorithm itself.
- **[WEB]** The function runs in the browser (TypeScript) and drives the Web Audio API. The hash must use explicit 32-bit integer arithmetic (not language-default string hashing) so results are identical across environments. In-memory memoization per signature is allowed, since it caches a pure function and is not a stored mapping.
- Implication for data modeling: there is **no persistent "Sound Mapping" entity/table**. Sound is a computed/derived attribute of an Event Type, not stored data.

---

## 9. Labeling Model

- **v1: Mechanical Labeling.** Labels are literal and structural, drawn directly from the code (e.g., "Line 14: `x = x + 1`", "Condition `arr[i] > arr[j]` → true", "Entering `for` loop, iteration 3"). No interpretation of intent.
- **v2: Semantic Labeling (future).** Labels add interpretive/conceptual meaning beyond the literal code (e.g., "This is the recursion's base case," "This is the left pointer"). Out of scope for v1 but should be considered as an extensibility point in the data model (e.g., an Event or Code Block entity should be able to later carry an optional "semantic tag" without a schema overhaul).

---

## 10. Key Entities (for ERD)

Attributes are illustrative, not exhaustive. Refine cardinalities and keys as needed.

1. **Project**
   - Represents a single submission.
   - Attributes: project_id, created_at, status (valid/invalid/pending).
   - Relationship: A Project **has many** Source Files, and **has many** ExecutionJobs.

2. **SourceFile**
   - Attributes: file_id, file_name, file_content, is_main_file (boolean).
   - Relationship: A SourceFile **has many** CodeElements. A SourceFile **belongs to** one Project.

3. **CodeElement** (class or method, depending on granularity needed)
   - Attributes: element_id, element_type (class/method), name, start_line, end_line.
   - Relationship: A CodeElement **belongs to** one SourceFile. A CodeElement **can call** other CodeElements (many-to-many, self-referencing).

4. **ExecutionJob** **[WEB, new]**
   - Represents one server-side compile-and-trace run.
   - Attributes: job_id, status (queued/compiling/running/complete/failed/cancelled), created_at, finished_at, limits_applied (timeout, memory, trace cap), error_code (nullable), error_message (nullable).
   - Relationship: An ExecutionJob **belongs to** one Project. An ExecutionJob **produces** zero or one ExecutionTrace.
   - Persistence: transient on the server (short TTL); not required to be a long-term table.

5. **ExecutionTrace**
   - Represents one full run/trace generated for a job.
   - Attributes: trace_id, created_at, total_event_count, schema_version **[WEB]**.
   - Relationship: An ExecutionTrace **belongs to** one ExecutionJob. An ExecutionTrace **has many** TraceEvents.

6. **TraceEvent**
   - Attributes: event_id, sequence_number, event_type (assignment/comparison/branch/loop/call/return/cross-file-access), line_number, source_file_id (FK), variable_name (nullable), old_value (nullable), new_value (nullable), condition_text (nullable), related_element_id (FK, nullable, for calls).
   - Relationship: A TraceEvent **belongs to** one ExecutionTrace. A TraceEvent **references** one SourceFile and optionally one CodeElement.
   - Derived (not stored): audio tone parameters, computed on demand via the deterministic sound function (Section 8).

7. **DataStructureState** (snapshot entity for visualization)
   - Attributes: state_id, trace_event_id (FK), structure_type (array/list/tree/etc.), structure_name, serialized_state.
   - Relationship: A DataStructureState **belongs to** one TraceEvent (each event may produce zero or more updated structure states).

8. **PlaybackSession** (client-only UI state; **not persisted**, not part of the server ERD)
   - Attributes: current_event_index, playback_speed, is_playing (boolean).
   - Relationship: A PlaybackSession **references** one ExecutionTrace (in browser memory).

**Relationship summary:**
- Project (1) ── (many) SourceFile
- SourceFile (1) ── (many) CodeElement
- CodeElement (many) ── (many) CodeElement [calls relationship, self-referencing]
- Project (1) ── (many) ExecutionJob
- ExecutionJob (1) ── (0..1) ExecutionTrace
- ExecutionTrace (1) ── (many) TraceEvent
- TraceEvent (many) ── (1) SourceFile
- TraceEvent (many) ── (0..1) CodeElement
- TraceEvent (1) ── (0..many) DataStructureState
- ExecutionTrace (1) ── (0..1 active) PlaybackSession [client-side only]

---

## 11. Key Classes / Modules (for UML Class Diagram)

The system is now split into **backend (Java)** and **frontend (TypeScript)**. They share only the versioned JSON trace/API contract.

### Backend (Java)

**Input/Validation package**
- `CodeSubmission`: holds submitted files/text, validation status.
- `JavaSourceFile`: represents one file, its parsed structure.
- `CodeValidator`: compiles/checks code, returns errors or a validated `CompiledProject`.

**Execution/Tracing package**
- `ExecutionEngine`: runs the compiled code under JDI instrumentation inside the sandbox.
- `TraceRecorder`: listens to execution and builds an ordered list of `TraceEvent` objects.
- `TraceEvent` (data class) and `EventType` (enum): ASSIGNMENT, COMPARISON, BRANCH, LOOP_ITERATION, METHOD_CALL, METHOD_RETURN, CROSS_FILE_ACCESS, etc.
- `TraceSerializer`: converts the trace to the versioned JSON format (compressed). **[WEB, new]**

**Sandbox package** **[WEB, new]**
- `SandboxRunner`: starts and tears down an isolated container/VM per job and applies resource limits.
- `LimitPolicy`: timeout, memory, output, and trace-size limits.

**API/Service package** **[WEB, new]**
- `JobController`: HTTP endpoints to submit, poll/stream status, cancel, and download the trace.
- `JobQueue` / `JobService`: queues jobs, enforces concurrency, stores results with a TTL.
- `RateLimiter`: per-client submission limits.
- DTOs: `SubmissionRequest`, `JobStatusResponse`, `TraceResponse`.

### Frontend (TypeScript)

**Editor/Input module**
- `EditorPanel`: code editor with syntax highlighting (Monaco/CodeMirror), upload/drag-and-drop.
- `ApiClient`: submits jobs, polls/streams status, downloads and validates the trace against the schema.

**Visualization module**
- `VisualizationController`: drives what's rendered based on current playback position.
- `DataStructureRenderer` (interface): implemented per structure type (e.g., `ArrayRenderer`), rendered with SVG/Canvas.
- `MultiFileViewManager`: manages simultaneous display of multiple file views and cross-file connector lines.
- `LabelGenerator`: produces mechanical labels from a `TraceEvent` (with an extension point for future semantic labels).

**Audio module**
- `ToneGenerator`: deterministic function: event signature → audio parameters (explicit 32-bit integer hashing).
- `AudioPlayer`: plays tones via the Web Audio API, scheduled on the audio context clock, unlocked by a user gesture.

**Playback module**
- `PlaybackController`: handles play/pause/speed/step-forward/step-backward/rewind commands.
- `PlaybackState`: current position, speed, playing status (client-only).

**Suggested key relationships:**
- `ExecutionEngine` *uses* `TraceRecorder` to produce a list of `TraceEvent`; `TraceSerializer` exports it; `ApiClient` imports it in the browser.
- `VisualizationController` and `AudioPlayer` both *consume* the same trace event stream (decoupled from each other, driven by `PlaybackState`).
- `LabelGenerator` and `ToneGenerator` both *depend on* `EventType`/`TraceEvent` but not on each other.
- `MultiFileViewManager` *is used by* `VisualizationController` specifically when a `TraceEvent` of type `CROSS_FILE_ACCESS` or `METHOD_CALL` (targeting a different file) occurs.

---

## 12. Non-Functional Requirements

- **Reliability:** The system must not crash on valid Java input; it must gracefully report and halt on invalid input. A crashing or hanging student program must never affect the server or other users.
- **Determinism:** Given the same code and the same run, the trace, visualization, and audio output must be identical every time, including across browsers (critical for the "same sound every time" requirement).
- **Performance:** Playback controls (step, pause, speed change) should feel responsive (no perceptible lag) even on longer traces. **[WEB]** Target: job turnaround for typical student programs within a few seconds (exact target to be set after the proof-of-concept).
- **Usability:** Mechanical labels must be legible and unambiguous without requiring the student to consult external documentation.
- **Extensibility:** The architecture should allow semantic labeling (v2) and auto-fix suggestions (v2) to be added without a full redesign of the trace/event model.
- **Security [WEB, new]:** Submitted code is untrusted. It must run with no network, no access to host files or secrets, enforced CPU/memory/time limits, and no ability to persist between jobs. The API must validate input sizes and rate-limit clients.
- **Scalability [WEB, new]:** The system must handle concurrent jobs via a queue with a bounded worker pool, and degrade gracefully (clear "busy" message) rather than fail.
- **Browser support [WEB, new]:** Current versions of Chrome, Edge, Firefox, and Safari (desktop). Audio depends on Web Audio API support.
- **Trace size [WEB, new]:** Maximum event count and download size are enforced (FR20); traces are compressed in transit.
- **Privacy [WEB, new]:** Submitted source code and traces are deleted from the server after a short retention period (value TBD).

---

## 13. Assumptions & Constraints

- Input code is assumed to be syntactically and logically "working" (compiles and runs with 0 errors); the system does not attempt to fix broken code in v1.
- v1 supports Java only, including multi-file/multi-class projects.
- **[WEB]** No user accounts or saved history in v1. Clients are anonymous and identified only for rate limiting (e.g., by IP or session). Submissions are held temporarily on the server (see Privacy) and are not shared between users.
- **[WEB]** A server with a JDK supporting JDI and a container/VM sandbox technology is available for hosting.
- Sound output is generated algorithmically at runtime in the browser; no external sound-asset library or persistent sound-mapping database is required.

*(Replaced from v1.0: "single local session per use" and "no history persistence across sessions".)*

---

## 14. Open Items for Your Project Partner to Resolve While Diagramming

- Exact granularity of "CodeElement" (class-level vs. method-level vs. both) for the UML/ERD.
- ~~Whether `DataStructureState` snapshots are stored fully or as diffs~~ Partly resolved by ADR-3 (full snapshots), but **revisit under the web trace-size cap** (e.g., keyframes plus diffs, see ADR-3).
- ~~Whether `PlaybackSession` needs persistence~~ Resolved: client-only (ADR-4).
- Precise list of `EventType` enum values to finalize before drawing the UML enum/class relationships.
- **[WEB]** Sandbox technology (Docker + seccomp, gVisor, Firecracker) and hosting provider (see ADR-7, ADR-9).
- **[WEB]** Concrete numeric limits: timeout, memory, output cap, max trace events, max trace size, rate limit, retention period.
- **[WEB]** Trace delivery: one batch download vs. streaming (see ADR-8).
- **[WEB]** Frontend framework choice (see ADR-6).
- **[WEB]** Whether to add an Operator role and basic monitoring for a public deployment.

---

*End of PRD v2.0 (Web)*
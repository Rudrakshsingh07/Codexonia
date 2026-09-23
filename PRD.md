# Product Requirements Document (PRD)
## Project Name: CodeSense (working title)
### Version: 1.0 (v1 scope only, unless marked "Future/v2")

---

## 1. Overview

CodeSense is a **JavaFX desktop application**, built primarily in Java, that helps Computer Science students understand **working, error-free Java code** written by someone else (a LeetCode solution, a senior's code, a teacher's example, etc.) by generating a **synchronized visual animation of the code's data structures** combined with **deterministic audio cues**, tied to the code's actual execution trace.

The core problem CodeSense solves: a student can read code and see that it *works*, but still cannot mentally trace **why control flows the way it does** (branches, loops, method calls) or **why data changes the way it does** (variable/value mutations, and exactly which line caused them). CodeSense makes both of these observable — visually and audibly — instead of requiring the student to trace it by hand.

---

## 2. Goals & Non-Goals

### 2.1 Goals (v1)
- Accept working, multi-file Java source code (via in-app text editor or file upload).
- Execute/trace the code and capture every relevant runtime event (assignment, comparison, branch decision, loop iteration, method call/return, cross-file data access).
- Render an animated, fully **mechanically labeled** visual representation of data structures as they change.
- Generate a **deterministic, algorithmically derived audio tone** for every distinct event type — no manually pre-mapped sound library, and no persistent sound-mapping storage required.
- Provide **video-player-style playback controls**, plus single-step forward/backward execution control.
- Detect and report compile/runtime errors without attempting to fix them.
- Visually represent multi-file execution: when execution or data pulls come from a file other than the main file, show the relevant files simultaneously and draw an explicit connection (e.g., a line to a "data source" block) at the moment the cross-file access occurs.

### 2.2 Non-Goals (explicitly out of scope for v1)
- Auto-correcting or suggesting fixes for broken code (planned for v2).
- Semantic/interpretive labeling (e.g., "this is the partition step," "this is the base case") — v1 is mechanical/literal labeling only (v2 feature).
- Support for languages other than Java.
- Explaining *why an algorithmic approach works* at a conceptual level (e.g., "why two-pointer works here") — CodeSense addresses control/data flow, not algorithmic insight.
- Web-based delivery — v1 is a JavaFX desktop application only.

---

## 3. Target Users & Personas

**Primary user: CS Student**
- Has found or been given working Java code they did not write.
- Understands basic programming concepts but cannot mentally simulate execution of the specific code in front of them.
- Wants to *see* and *hear* what the code does, step by step, at their own pace.

**Example scenarios:**
1. A student solves a LeetCode problem incorrectly, looks up the accepted solution, but still can't understand its logic. They paste the solution into CodeSense.
2. A student receives multi-file example code from a senior or instructor illustrating a topic (e.g., OOP, recursion, data structures) and cannot trace how data or control moves across files.

---

## 4. User Roles / Actors (for Use Case Diagram)

| Actor | Description |
|---|---|
| **Student (Primary Actor)** | Uploads/edits code, controls playback, views/listens to the visualization. |
| **CodeSense System** | The application itself — parses, executes/traces, visualizes, and sonifies code. |
| **Java Compiler/Runtime (Secondary/Supporting Actor)** | External system used by CodeSense to compile and execute the submitted code. |

*(Note: v1 has a single human actor — the Student. There is no teacher/admin role in v1.)*

---

## 5. Use Cases

1. **Submit Code via Text Editor**
   Student types or pastes Java code into the in-app editor (with syntax highlighting/beautification and editing support).

2. **Submit Code via File Upload**
   Student uploads one or more `.java` files (multi-file project support).

3. **Validate Code**
   System compiles/checks the submitted code. If errors exist, the system reports them to the student and halts (no auto-fix, no visualization generated).

4. **Generate Execution Trace**
   System executes the validated code and records a structured sequence of runtime events (assignments, comparisons, branches, loop iterations, method calls/returns, cross-file data/method access).

5. **View Visualization**
   Student watches an animated, labeled representation of data structures and control flow, synchronized with generated audio cues.

6. **Control Playback**
   Student uses play, pause, speed up, slow down, fast-forward, rewind, step-forward (single execution step), and step-backward (single execution step) controls.

7. **Observe Cross-File Execution**
   When execution or data access crosses into another file, the system displays the relevant files simultaneously and visually indicates the connection (e.g., a line from the calling block to the source block) at the moment it occurs.

8. **Hear Audio Cue for Event**
   For every event in the trace, the system plays a tone. If the event type has occurred before, the same tone plays as before (consistency). If the event type is new, the system algorithmically derives a new deterministic tone for it (see Section 8).

---

## 6. Functional Requirements

### 6.1 Input
- FR1: The system shall accept Java source code via an in-app text editor.
- FR2: The text editor shall provide syntax highlighting/beautification and allow direct editing.
- FR3: The system shall accept one or more `.java` files via file upload (multi-file projects).
- FR4: The system shall only accept code that compiles and runs with zero errors; if errors are present, the system shall display the error(s) to the student without modifying the code.

### 6.2 Execution & Tracing
- FR5: The system shall compile and execute submitted Java code in a controlled runtime.
- FR6: The system shall capture a structured, ordered trace of execution events, at minimum including:
  - Variable declaration/assignment (with old value, new value, line number, file).
  - Conditional evaluation (condition text, result, line number, file).
  - Branch entry (which branch of an if/else/switch was taken).
  - Loop iteration start/end (loop type, iteration count, line number, file).
  - Method call (caller file/class/method, callee file/class/method, arguments, line number).
  - Method return (return value, line number).
  - Cross-file data or method access (source file/class, destination file/class, what was accessed).
- FR7: Each trace event shall record enough metadata to support both the visual animation and the audio cue generation.

### 6.3 Visualization
- FR8: The system shall animate data structures (e.g., arrays, and other structures as needed) reflecting their state changes over time, in sync with the trace.
- FR9: All visual elements shall be **mechanically labeled** — i.e., labeled using the code's own literal content (variable names, line numbers, condition text, method names) with nothing left for the student to infer or guess.
- FR10: When execution enters a method defined in a file other than the currently focused file, the system shall display the relevant files simultaneously (not merely switch views).
- FR11: When data is accessed from another file, the system shall draw a visible connecting line from the accessing block to the source block, appearing at the moment the access occurs.

### 6.4 Audio
- FR12: The system shall generate an audio tone for every trace event.
- FR13: The tone for a given event type shall be produced via a deterministic algorithm (e.g., a hash/mapping function from event-type signature to frequency/timbre parameters), not via a stored/retrieved lookup table.
- FR14: The same event type shall always produce the same tone, including event types encountered for the first time during a given session (the algorithm must generalize to unseen event types without prior storage).

### 6.5 Playback Controls
- FR15: The system shall provide Play, Pause, Fast-Forward, Rewind, and Speed adjustment controls, consistent with standard video-player conventions.
- FR16: The system shall provide a Step-Forward control that advances the visualization by exactly one execution event.
- FR17: The system shall provide a Step-Backward control that reverses the visualization by exactly one execution event.

---

## 7. Data Flow Overview (for Data Flow Diagram)

**External Entity:** Student

**High-level process flow:**

1. **Student** → (Java source code, via editor or file upload) → **Input Handling Process**
2. **Input Handling Process** → (validated source / error report) → **Compilation & Validation Process**
   - If invalid → (error message) → **Student**
   - If valid → proceeds
3. **Compilation & Validation Process** → (compiled/executable code) → **Execution & Tracing Process**
4. **Execution & Tracing Process** → (ordered stream of Trace Events) → **Trace Event Store** (in-memory structure for the session)
5. **Trace Event Store** → (event stream) → **Visualization Rendering Process**
6. **Trace Event Store** → (event stream) → **Audio Generation Process**
7. **Visualization Rendering Process** → (animated frames) → **Student**
8. **Audio Generation Process** → (generated tone/audio signal) → **Student**
9. **Student** → (playback control commands: play/pause/step/speed/rewind) → **Playback Controller Process**
10. **Playback Controller Process** → (position/speed state) → **Trace Event Store** (controls which events are currently being rendered/played)

**Key data stores:**
- Source Code Files (input, multi-file)
- Trace Event Store (sequential events with metadata: type, line, file, variable/values, call context)
- Tone-Generation Function output is computed on demand — **not persisted** (per FR13/FR14, deterministic algorithm replaces storage).

---

## 8. Sound Generation Model

- Each **event type** (a category, e.g., "variable assignment," "loop iteration," "comparison," "method call," "cross-file access," etc.) is mapped to a set of identifying characteristics (an "event signature").
- A **deterministic function** (e.g., a hash function feeding into a frequency/timbre-selection formula) takes the event signature as input and produces the same audio output (frequency, and optionally instrument/timbre/duration) every time it is given that same signature — whether the signature was seen before in this session or not.
- This removes the need for a stored, retrievable sound-mapping table/database; the "memory" of which sound belongs to which event is implicit in the deterministic algorithm itself.
- Implication for data modeling: there is **no persistent "Sound Mapping" entity/table** in the data model — sound is a computed/derived attribute of an Event Type, not stored data.

---

## 9. Labeling Model

- **v1 — Mechanical Labeling:** Labels are literal and structural, drawn directly from the code (e.g., "Line 14: `x = x + 1`", "Condition `arr[i] > arr[j]` → true", "Entering `for` loop — iteration 3"). No interpretation of intent.
- **v2 — Semantic Labeling (future):** Labels add interpretive/conceptual meaning beyond the literal code (e.g., "This is the recursion's base case," "This is the left pointer"). Out of scope for v1 but should be considered as an extensibility point in the data model (e.g., an Event or Code Block entity should be able to later carry an optional "semantic tag" without a schema overhaul).

---

## 10. Key Entities (for ERD)

These are the core conceptual entities and their relationships. Attributes are illustrative, not exhaustive — your partner should refine cardinalities and keys as needed.

1. **Project**
   - Represents a single submission session.
   - Attributes: project_id, created_at, status (valid/invalid/pending).
   - Relationship: A Project **has many** Source Files.

2. **SourceFile**
   - Attributes: file_id, file_name, file_content, is_main_file (boolean).
   - Relationship: A SourceFile **has many** CodeElements (classes/methods). A SourceFile **belongs to** one Project.

3. **CodeElement** (class or method, depending on granularity needed)
   - Attributes: element_id, element_type (class/method), name, start_line, end_line.
   - Relationship: A CodeElement **belongs to** one SourceFile. A CodeElement **can call** other CodeElements (many-to-many, self-referencing — this supports cross-file call visualization).

4. **ExecutionTrace**
   - Represents one full run/trace generated for a Project.
   - Attributes: trace_id, created_at, total_event_count.
   - Relationship: An ExecutionTrace **belongs to** one Project. An ExecutionTrace **has many** TraceEvents.

5. **TraceEvent**
   - Attributes: event_id, sequence_number, event_type (assignment/comparison/branch/loop/call/return/cross-file-access), line_number, source_file_id (FK), variable_name (nullable), old_value (nullable), new_value (nullable), condition_text (nullable), related_element_id (FK, nullable — for calls).
   - Relationship: A TraceEvent **belongs to** one ExecutionTrace. A TraceEvent **references** one SourceFile and optionally one CodeElement.
   - Derived (not stored): audio tone parameters, computed on demand from event_type + relevant attributes via the deterministic sound function (Section 8).

6. **DataStructureState** (snapshot entity for visualization)
   - Attributes: state_id, trace_event_id (FK), structure_type (array/list/tree/etc.), structure_name, serialized_state (representation of values at this point in time).
   - Relationship: A DataStructureState **belongs to** one TraceEvent (each event may produce zero or more updated structure states).

7. **PlaybackSession** (runtime/UI state, may be transient rather than persisted)
   - Attributes: current_event_index, playback_speed, is_playing (boolean).
   - Relationship: A PlaybackSession **references** one ExecutionTrace.

**Relationship summary:**
- Project (1) ── (many) SourceFile
- SourceFile (1) ── (many) CodeElement
- CodeElement (many) ── (many) CodeElement [calls relationship, self-referencing]
- Project (1) ── (many) ExecutionTrace
- ExecutionTrace (1) ── (many) TraceEvent
- TraceEvent (many) ── (1) SourceFile
- TraceEvent (many) ── (0..1) CodeElement
- TraceEvent (1) ── (0..many) DataStructureState
- ExecutionTrace (1) ── (0..1 active) PlaybackSession

---

## 11. Key Classes (for UML Class Diagram)

Suggested class groupings by responsibility (package-level thinking, not final implementation):

**Input/Parsing package**
- `CodeSubmission` — holds submitted files/text, validation status.
- `JavaSourceFile` — represents one file, its parsed structure.
- `CodeValidator` — compiles/checks code, returns errors or a validated `CompiledProject`.

**Execution/Tracing package**
- `ExecutionEngine` — runs the compiled code under instrumentation.
- `TraceRecorder` — listens to execution and builds an ordered list of `TraceEvent` objects.
- `TraceEvent` (data class) — as described in Section 10.
- `EventType` (enum) — ASSIGNMENT, COMPARISON, BRANCH, LOOP_ITERATION, METHOD_CALL, METHOD_RETURN, CROSS_FILE_ACCESS, etc.

**Visualization package**
- `VisualizationController` — drives what's rendered based on current playback position.
- `DataStructureRenderer` (interface) — implemented per structure type (e.g., `ArrayRenderer`).
- `MultiFileViewManager` — manages simultaneous display of multiple file views and cross-file connector lines.
- `LabelGenerator` — produces mechanical labels from a `TraceEvent` (with an extension point for future semantic labels).

**Audio package**
- `ToneGenerator` — deterministic function: `EventType`/event signature → audio output parameters.
- `AudioPlayer` — plays generated tones in sync with playback position.

**Playback package**
- `PlaybackController` — handles play/pause/speed/step-forward/step-backward/rewind commands.
- `PlaybackState` — current position, speed, playing status.

**Suggested key relationships:**
- `ExecutionEngine` *uses* `TraceRecorder` to produce a list of `TraceEvent`.
- `VisualizationController` and `AudioPlayer` both *consume* the same `TraceEvent` stream (decoupled from each other).
- `LabelGenerator` and `ToneGenerator` both *depend on* `EventType`/`TraceEvent` but not on each other.
- `MultiFileViewManager` *is used by* `VisualizationController` specifically when a `TraceEvent` of type `CROSS_FILE_ACCESS` or `METHOD_CALL` (targeting a different file) occurs.

---

## 12. Non-Functional Requirements

- **Reliability:** The system must not crash on valid Java input; it must gracefully report and halt on invalid input.
- **Determinism:** Given the same code and the same run, the trace, visualization, and audio output must be identical every time (critical for the "same sound every time" requirement).
- **Performance:** Playback controls (step, pause, speed change) should feel responsive (no perceptible lag) even on longer traces.
- **Usability:** Mechanical labels must be legible and unambiguous without requiring the student to consult external documentation.
- **Extensibility:** The architecture should allow semantic labeling (v2) and auto-fix suggestions (v2) to be added without a full redesign of the trace/event model.

---

## 13. Assumptions & Constraints

- Input code is assumed to be syntactically and logically "working" (compiles and runs with 0 errors); the system does not attempt to fix broken code in v1.
- v1 supports Java only, including multi-file/multi-class projects.
- No user accounts, history persistence across sessions, or multi-user features are assumed for v1 (single local session per use).
- Sound output is generated algorithmically at runtime; no external sound-asset library or persistent sound-mapping database is required.

---

## 14. Open Items for Your Project Partner to Resolve While Diagramming

- Exact granularity of "CodeElement" (class-level vs. method-level vs. both) for the UML/ERD.
- Whether `DataStructureState` snapshots are stored fully per event (simpler, more storage) or as diffs (more complex, less storage) — affects ERD attributes.
- Whether `PlaybackSession` needs persistence at all, or is purely in-memory UI state (affects whether it appears in the ERD or only the UML).
- Precise list of `EventType` enum values to finalize before drawing the UML enum/class relationships.

---

*End of PRD v1.0*

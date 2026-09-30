# Codexonia

## Software Requirements Specification (SRS)

*Version 2.0 (Web edition) | Prepared for Weekly Project Review*

*Team: Dev A, Dev B, Dev C*

> **Change note (v1.0 desktop → v2.0 web):** The product is now a web application. Trace generation runs on a sandboxed server; visualization, audio, and playback run in the browser. Changed or new items are marked **[WEB]**.

---

# 1. Introduction

## 1.1 Purpose

This document specifies the functional and non-functional requirements for Codexonia v2.0, a **web application** that helps Computer Science students understand working, error-free Java code by generating a synchronized visual animation of the code's data structures, paired with deterministic audio cues, tied to the code's actual execution trace. It is intended for the development team, the course instructor, and any reviewer assessing project scope and progress.

## 1.2 Scope

Codexonia accepts multi-file Java source code in the browser, sends it to a server that validates that it compiles and runs without error, executes it in a sandbox under instrumentation to capture a structured trace of runtime events, and delivers that trace to the browser as JSON. The browser plays the trace back as a mechanically labeled visual animation with synchronized, algorithmically generated audio, with video-player-style playback controls including single-step forward and backward. It does not attempt to fix broken code, does not provide semantic/conceptual explanations of algorithms, and does not support languages other than Java. It has no user accounts and no cross-session history in v1; submissions are held only temporarily on the server.

## 1.3 Definitions, Acronyms and Abbreviations

| **Term** | **Definition** |
| --- | --- |
| Trace Event | A single recorded occurrence during execution (assignment, comparison, branch, loop iteration, call, return, or cross-file access). |
| Mechanical Labeling | Labeling drawn literally from code content (variable names, line numbers, condition text) with no interpretive/conceptual meaning added. |
| JDI | Java Debug Interface, the mechanism used to instrument and step through target program execution. |
| Event Signature | A normalized identifying key for an event type/category, used as input to the deterministic tone function. |
| Sandbox **[WEB]** | An isolated container/VM with no network and enforced resource limits, in which submitted code is compiled and run. |
| Job **[WEB]** | One asynchronous server-side compile-and-trace request, with a status lifecycle. |
| Trace Schema **[WEB]** | The versioned JSON format of the trace, shared between backend and frontend. |
| FR / NFR | Functional Requirement / Non-Functional Requirement. |

## 1.4 References

- Codexonia Product Requirements Document (PRD), v2.0
- Codexonia Architecture Decision Records (ADR Log), v2.0
- Codexonia Tone Hash Function: Design Decisions, v2.0

---

# 2. Overall Description

## 2.1 Product Perspective

Codexonia is a client-server web application. The **frontend** (TypeScript, runs in the user's browser) provides the editor, visualization, audio, and playback. The **backend** (Java) provides the job API, compilation, sandboxed execution, and tracing. The backend depends on a JDK with JDI and a sandbox technology as supporting external systems. The client needs network connectivity only to submit a job and download its trace; playback itself works entirely on the downloaded trace.

## 2.2 Product Functions (Summary)

- Accept Java source via an in-browser editor or file upload/drag-and-drop (single or multi-file).
- Validate that submitted code compiles and runs with zero errors; report errors without attempting fixes.
- **[WEB]** Run submitted code as an asynchronous, cancellable server job inside a sandbox with enforced limits.
- Execute the code under instrumentation and capture an ordered, structured trace of runtime events.
- **[WEB]** Deliver the trace to the browser as compressed, versioned JSON.
- Render an animated, mechanically labeled visualization of data structures synchronized to the trace.
- Generate a deterministic audio tone for every event type via an algorithmic function (no stored sound library), played through the Web Audio API.
- Display multiple files simultaneously with visible connector lines when execution or data access crosses file boundaries.
- Provide play, pause, speed control, fast-forward, rewind, step-forward, and step-backward playback controls.

## 2.3 User Characteristics

The primary user is a Computer Science student who understands basic programming concepts and can read working Java code, but cannot easily mentally simulate its execution. No special technical training beyond general Java familiarity is assumed. **[WEB]** The user needs only a current desktop browser and audio output.

## 2.4 Constraints

- v1 supports Java only, including multi-file/multi-class projects.
- Input code is assumed to already compile and run correctly; auto-fix is out of scope for v1.
- No user accounts, saved history, or multi-user features in v1.
- No external sound-asset library or persistent sound-mapping storage; tone generation must be computed on demand.
- **[WEB]** Web delivery only; no offline mode and no native mobile apps in v1.
- **[WEB]** Submitted code is untrusted and must run only inside the sandbox, within the limits defined in FR5 and FR18 to FR21.
- **[WEB]** Browsers require a user gesture before audio can start.

*(Removed from v2.0: "Desktop delivery only (JavaFX) - no web-based delivery in v1.")*

## 2.5 Assumptions and Dependencies

- **[WEB]** A server environment with a JDK supporting JDI is available (see ADR-1), together with a container or microVM sandbox technology (see ADR-7) and a hosting platform (see ADR-9).
- **[WEB]** The backend and frontend agree on a versioned Trace Schema (see ADR-8); neither depends on the other's internal classes.
- Determinism of trace, visualization, and audio output is required given identical input code and a single run (see NFR: Determinism), including across browsers.

---

# 3. Module Breakdown and Team Ownership

The system is divided into three ownership modules distributed across three developers. Module boundaries are drawn so that Visualization and Audio depend only on the shared Trace Schema and PlaybackState (see ADR-5), allowing the modules to be developed and tested in parallel. **[WEB]** Module A is in Java; Modules B and C are in TypeScript.

> **Workload note [WEB]:** Module A now also carries sandboxing, which makes it the highest-risk and heaviest module. To rebalance, the job API, queue, rate limiting, and deployment (component "Platform & API Service") are assigned to Dev C, who gains capacity because Web Audio replaces hand-rolled audio output. Revisit this split after the JDI and sandbox proofs of concept.

## 3.1 Module A: Input Validation, Execution Engine & Sandbox (Owner: Dev A, Java)

Owns getting Java source validated and executed under instrumentation inside a sandbox, with a complete event trace serialized out the other end.

| **Component** | **Responsibility** |
| --- | --- |
| CodeSubmission | Holds submitted files/text and validation status. |
| JavaSourceFile | Represents one source file and its parsed structure. |
| CodeValidator | Compiles/checks code; returns errors or a validated CompiledProject. |
| ExecutionEngine | Runs the compiled code under JDI instrumentation. |
| TraceRecorder | Listens to execution and builds the ordered TraceEvent list. |
| TraceEvent / EventType | Core data model and enum for all recorded runtime events. |
| TraceSerializer **[WEB]** | Converts the trace to the versioned, compressed JSON format. |
| SandboxRunner **[WEB]** | Starts and tears down the isolated environment per job and applies resource limits. |
| LimitPolicy **[WEB]** | Timeout, memory, output, and trace-size limits. |

Satisfies: FR3 (server side), FR4 to FR7, FR18, FR20. Related: ADR-1, ADR-3, ADR-4, ADR-7, ADR-8.

## 3.2 Module B: Editor, Visualization & Multi-File Rendering (Owner: Dev B, TypeScript)

Owns the in-browser editor and turning the trace stream into an animated, mechanically labeled, multi-file-aware visual.

| **Component** | **Responsibility** |
| --- | --- |
| EditorPanel | In-browser code editor with syntax highlighting and upload/drag-and-drop (Proposed: Monaco or CodeMirror). |
| VisualizationController | Drives what's rendered based on current playback position. |
| DataStructureRenderer (+ ArrayRenderer, etc.) | Per-structure-type rendering of data structure state (SVG/Canvas). |
| MultiFileViewManager | Manages simultaneous display of multiple files and cross-file connector lines. |
| LabelGenerator | Produces mechanical labels from a TraceEvent; extension point for future semantic labels. |

Satisfies: FR1, FR2, FR3 (client side), FR8 to FR11. Related: ADR-3, ADR-5, ADR-6.

## 3.3 Module C: Audio, Playback, Platform API & App Shell (Owner: Dev C, TypeScript + Java service)

Owns sound generation, playback state and controls, the job API and deployment, and the web app shell that hosts Modules A's output and B's views together.

| **Component** | **Responsibility** |
| --- | --- |
| ToneGenerator | Deterministic function mapping event signature to audio output parameters (explicit 32-bit integer hashing). |
| AudioPlayer | Plays tones via the Web Audio API, scheduled on the audio context clock, unlocked by a user gesture. |
| PlaybackController | Handles play/pause/speed/step-forward/step-backward/rewind commands. |
| PlaybackState | Current position, speed, and playing status (transient, client-side only). |
| Web App Shell **[WEB]** | Top-level page, layout, routing, and integration of Modules A (via API) and B. |
| ApiClient **[WEB]** | Submits jobs, polls/streams status, downloads and schema-validates the trace. |
| JobController / JobService **[WEB]** | Backend endpoints and queue for submit, status, cancel, and trace download; result TTL. |
| RateLimiter **[WEB]** | Per-client submission limiting. |

Satisfies: FR12 to FR17, FR19, FR21, FR22. Related: ADR-2, ADR-4, ADR-5, ADR-6, ADR-8, ADR-9, ADR-10.

## 3.4 Shared Interface Contract

All modules agree on a single shared contract, owned by Dev A and frozen early to unblock parallel work: the **versioned JSON Trace Schema** (TraceEvent stream plus DataStructureState snapshots) and the **job API specification** (OpenAPI). **[WEB]** The contract is now language-neutral: Java classes on the backend and TypeScript types on the frontend are both derived from or validated against the same schema. PlaybackState is a frontend-only contract between Modules B and C. Modules B and C consume the trace independently and never call one another directly (ADR-5).

---

# 4. Specific Requirements

## 4.1 Functional Requirements

| **ID** | **Requirement** | **Module** |
| --- | --- | --- |
| FR1 | Accept Java source code via an in-browser text editor. | B |
| FR2 | Editor provides syntax highlighting and direct editing. | B |
| FR3 | Accept one or more .java files via browser upload/drag-and-drop (multi-file), with size limits. | A (server), B (client) |
| FR4 | Only accept code that compiles/runs with zero errors; display errors otherwise. | A |
| FR5 | **[WEB]** Compile and execute submitted code on the server in a sandbox with CPU, memory, and wall-clock limits, no network, and an isolated ephemeral filesystem. | A |
| FR6 | Capture a structured, ordered trace of execution events with required metadata. | A |
| FR7 | Each trace event records enough metadata for both visualization and audio. | A |
| FR8 | Animate data structures reflecting state changes in sync with the trace. | B |
| FR9 | All visual elements are mechanically labeled from the code's own content. | B |
| FR10 | Display relevant files simultaneously on cross-file method entry. | B |
| FR11 | Draw a visible connector line at the moment of cross-file data access. | B |
| FR12 | Generate an audio tone for every trace event. | C |
| FR13 | Tone for a given event type is produced via a deterministic algorithm. | C |
| FR14 | Same event type always produces the same tone, including on first occurrence, identical across browsers. | C |
| FR15 | Provide Play, Pause, Fast-Forward, Rewind, and Speed controls. | C |
| FR16 | Provide Step-Forward control advancing exactly one event. | C |
| FR17 | Provide Step-Backward control reversing exactly one event. | C |
| FR18 | **[WEB]** Enforce and report execution limits (timeout, memory, stdout/stderr cap, no network) and apply a defined stdin policy. | A |
| FR19 | **[WEB]** Run validation and tracing as asynchronous, cancellable jobs with visible status (queued, compiling, running, complete, failed). | C |
| FR20 | **[WEB]** Enforce a maximum trace size (events and bytes); fail with a clear message if exceeded; deliver traces compressed. | A |
| FR21 | **[WEB]** Rate-limit submissions per anonymous client. | C |
| FR22 | **[WEB]** Start audio only after a user gesture; schedule tones on the audio context clock. | C |

## 4.2 Non-Functional Requirements

| **Category** | **Requirement** |
| --- | --- |
| Reliability | Must not crash on valid Java input; gracefully reports and halts on invalid input. A crashing or hanging student program must never affect the server or other users. |
| Determinism | Same code and same run must always produce identical trace, visualization, and audio output, including across browsers. |
| Performance | Playback controls (step, pause, speed change) must feel responsive with no perceptible lag, even on long traces. **[WEB]** Job turnaround for typical student programs within a few seconds (target to be confirmed after proof-of-concept). |
| Usability | Mechanical labels must be legible and unambiguous without external documentation. |
| Extensibility | Architecture must allow v2 semantic labeling and auto-fix without a full redesign of the trace/event model. Trace Schema is versioned. |
| Security **[WEB]** | Submitted code is untrusted: no network, no host file or secret access, enforced resource limits, no persistence between jobs. API validates input sizes and rate-limits clients. |
| Scalability **[WEB]** | Concurrent jobs handled via a queue with a bounded worker pool; shows a clear "busy" message under load instead of failing. |
| Browser support **[WEB]** | Current desktop versions of Chrome, Edge, Firefox, and Safari. |
| Privacy **[WEB]** | Submitted code and traces are deleted from the server after a short retention period (value TBD). |

## 4.3 External Interface Requirements

### 4.3.1 User Interfaces

- In-browser code editor panel with syntax highlighting (Module B).
- Multi-file view panel capable of showing 2+ files simultaneously with connector overlays (Module B).
- Playback control bar (play/pause/step/speed/rewind) styled on standard video-player conventions (Module C).
- Error display panel for compile/runtime/limit error reporting (Modules A and C).
- **[WEB]** Job status indicator with a cancel button (Module C).

### 4.3.2 Hardware / Software Interfaces

- **[WEB]** HTTP(S) REST API (and optionally WebSocket/SSE for status) between browser and backend, described by an OpenAPI spec (Modules A and C).
- JDK with JDI support for compilation and instrumented execution, running inside the sandbox (Module A / ADR-1, ADR-7).
- **[WEB]** Web Audio API for all tone playback (Module C).
- **[WEB]** Browser rendering (SVG/Canvas/DOM) for all UI and animation (Modules B and C).

---

# 5. Requirements Traceability Summary

Every functional requirement maps to an owning module, and every module maps to at least one ADR governing its core technical approach. This traceability will be maintained as the design evolves into implementation, and updated diagrams (use case, activity, sequence, class, ERD) will reference these same FR/module identifiers.

| **Module** | **FRs Covered** | **Governing ADRs** |
| --- | --- | --- |
| A: Input Validation, Execution & Sandbox | FR3 (server), FR4 to FR7, FR18, FR20 | ADR-1, ADR-3, ADR-4, ADR-7, ADR-8 |
| B: Editor, Visualization & Multi-File Rendering | FR1, FR2, FR3 (client), FR8 to FR11 | ADR-3, ADR-5, ADR-6 |
| C: Audio, Playback, Platform API & Shell | FR12 to FR17, FR19, FR21, FR22 | ADR-2, ADR-4, ADR-5, ADR-6, ADR-8, ADR-9, ADR-10 |

---

# 6. Open Items

- Exact granularity of CodeElement (class-level vs. method-level) to finalize before UML/ERD are drawn.
- Final enumeration of EventType values, to be locked once Module A's proof-of-concept confirms what JDI can reliably capture.
- Frequency/timbre mapping design for ToneGenerator (functionally deterministic already; aesthetic tuning pending).
- **[WEB]** Sandbox technology choice and proof of concept (ADR-7).
- **[WEB]** Numeric limits: timeout, memory, output cap, max trace events/bytes, rate limit, retention period.
- **[WEB]** Trace delivery format: batch vs. streaming (ADR-8), and snapshot strategy under the size cap (ADR-3).
- **[WEB]** Hosting provider and deployment approach (ADR-9).
- **[WEB]** Whether the workload split in Section 3 needs a fourth owner or rebalancing.

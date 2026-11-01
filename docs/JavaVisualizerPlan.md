# Java Visualizer Core + Web UI — Plan

## Goal

Build the visual animation and audio part of Codexonia with its **logic and core
code in Java**, while the result is **visible in the existing browser UI** on the
same Spring Boot site.

This plan supersedes the earlier "JavaFX desktop visualizer" framing.

> **Status (implemented).** The v1 player ships on `/submit`. The Java core lives
> in `src/main/java/visualizer/` (`TimelineBuilder`, `SceneState`, `ToneSpec`,
> `ToneProvider`); `RunController` attaches its output as an additive `timeline`
> sibling of the trace JSON. The browser paints frames and drives the cursor,
> transport, and Web Audio. Lists/trees, simultaneous multi-file panes, and
> cross-file connectors remain future work (see §8).

### Requirement clarification

"Java only" means:

- Trace consumption and the trace model
- Frame semantics (what each cursor position shows, including step/scrub boundaries)
- Scene computation (what is visible at step N: active file/line, call stack,
  variables, data structures, stdout)
- Mechanical label generation
- Data-structure layout and animation hints
- Event signature / FNV-1a hash / tone parameters
- Visualization timeline generation

The browser may contain a **thin rendering layer** (HTML, CSS, Web Audio, and the
small amount of JavaScript needed to paint frames and drive the browser APIs).
It owns the wall clock and cursor; it must not interpret events or reconstruct
scene state. That layer must not contain trace interpretation or visualization
logic.

If the course/reviewer requires the **UI code itself** to be Java as well, the
fallback is TeaVM (see §7). The core and timeline contract stay the same.

---

## 1. Why not put JavaFX in the browser

JavaFX is a desktop toolkit. It cannot run in a normal browser tab, and Java
applets / Java Web Start are dead. The realistic ways to get Java logic into a
web UI are:

1. **Java on the server + browser renderer** *(chosen)*
2. **Java compiled to JavaScript/WASM with TeaVM** *(fallback if the UI code
   itself must be Java)*
3. **A Java web framework such as Vaadin** *(rejected for this project)*

The chosen approach keeps the existing Spring Boot app, serves the player from
the existing site shell, and puts all visualization decisions in Java. JavaScript
only draws what Java tells it to draw.

---

## 2. Implemented architecture

```
Browser: /submit timeline player + thin renderer
  ├─ receives the trace JSON plus an additive `timeline` sidecar
  ├─ paints code view, stack, variables, structures, stdout, event list
  ├─ applies CSS transitions using Java-provided animation hints
  ├─ plays Java-provided tone specs through Web Audio
  └─ owns the cursor/timer: play, pause, step, scrub, speed, audio toggle

Spring Boot
  ├─ POST /api/run
  │    ├─ RunnerEngine → trace JSON (existing contract)
  │    ├─ TimelineBuilder.build(submission, traceJson, ToneProvider)
  │    └─ TimelineBuilder.attach(traceJson, timelineJson)
  ├─ serves static images/JS/CSS for the player
  └─ scripts/tests use the same pure-Java core

src/main/java/visualizer/   (pure Java, no JavaFX, no Spring)
  ├─ TimelineBuilder.java   trace → SceneState[]; labels, stack, arrays, tones
  ├─ SceneState.java        frame + CallFrame + Variable + Structure + Geometry
  ├─ ToneSpec.java          one frame's Java-computed tone parameters
  └─ ToneProvider.java      framework-free hook; RunController wires HashFunction
```

The page and the API are served by the same Spring Boot process. There is no
separate frontend server and no TypeScript/React rewrite.

---

## 3. Trace + timeline contract

Keep the existing ADR-8 trace schema unchanged. Add a **visualization sidecar**
to the run response so the frozen trace contract does not have to change:

```json
{
  "schema_version": 1,
  "status": "COMPLETE",
  "sources": [
    {"file_name": "Sample.java", "is_main_file": true}
  ],
  "stdout": "5:5\n",
  "event_count": 12,
  "events": [ ... ],
  "timeline": {
    "timeline_schema_version": 1,
    "trace_schema_version": 1,
    "status": "COMPLETE",
    "sources": [
      {"file_name": "Sample.java", "is_main_file": true, "content": "public class Sample { ... }"}
    ],
    "frame_count": 12,
    "frames": [
      {
        "index": 8,
        "seq": 10,
        "event": { "type": "ASSIGNMENT", "file": "Sample.java", "line": 6 },
        "event_type": "ASSIGNMENT",
        "event_signature": "assignment",
        "active_file": "Sample.java",
        "active_line": 6,
        "label": {
          "title": "nums = [1, 2, 3]",
          "subtitle": "Line 6 · Sample.java"
        },
        "call_stack": [
          {"class": "Sample", "method": "main", "file": "Sample.java",
           "line": 6, "depth": 0, "args": ["[]"]}
        ],
        "variables": [
          {"name": "nums", "value": "[1, 2, 3]",
           "changed": true, "old": null, "new": "[1, 2, 3]"}
        ],
        "structures": [
          {
            "name": "nums",
            "kind": "array",
            "elements": ["1", "2", "3"],
            "changed_indices": [],
            "geometry": {
              "cells": [
                {"index": 0, "x": 0, "y": 0, "w": 48, "h": 40}
              ]
            }
          }
        ],
        "visible_files": ["Sample.java"],
        "connectors": [],
        "stdout_visible": "",
        "tone": {
          "signature": "assignment",
          "frequency_hz": 261.625565,
          "oscillator": "sine",
          "duration_ms": 200,
          "note_index": 1,
          "midi_note": 60,
          "hash": "64ed874e"
        },
        "animation": {
          "kind": "value_change",
          "duration_ms": 200
        }
      }
    ]
  }
}
```

Rules:

- Frames are indexed 0..N-1 and align one-to-one with trace events.
- All strings shown to the student are generated in Java (`TimelineBuilder`).
- All structure geometry is computed in Java. The renderer only draws the
  coordinates it receives.
- Source file **contents** are included in the timeline so the player does not
  depend on `localStorage` for source.
- The original trace fields remain unchanged. `timeline` is additive, so older
  clients can ignore it.
- A classified failure still returns a trace-shaped body and a timeline with
  `frame_count: 0`, `sources` (including contents), `status`, and
  `error_message`. The player shows the message and disables transport.
- `connectors` and `visible_files` are already in the schema for future
  multi-file/cross-file work; v1 draws one active file at a time and leaves
  `connectors` empty.

Size and limits:

- `TimelineBuilder.MAX_FRAMES = 8000` and `MAX_JSON_CHARS = 4_000_000`.
- If a trace exceeds either bound, the timeline is replaced with a
  `TIMELINE_TOO_LARGE` timeline (`frame_count: 0`, clear error message) and the
  raw trace stays available. The run itself is not failed.
- The full response remains a single HTTP payload; one execution, one request.

---

## 4. Java core design (implemented)

### 4.1 Trace model

The core reuses the existing runner model instead of duplicating it:

- `TraceEvent.fromJson(...)` parses the trace events.
- `Json` handles string escaping; no Jackson dependency in the core.
- Sources are copied into the timeline with `content`, while the trace keeps its
  original `sources` metadata.
- `SceneState` holds the per-frame display model; there is no JavaFX type in the
  core.

### 4.2 Playback

Frame order and frame contents are fixed in Java. The browser owns the wall clock
and cursor and only moves between precomputed frames:

- Play/pause, step, rewind/end, scrub, and speed change only the browser cursor.
- Loading a frame paints exactly the fields `TimelineBuilder` supplied.
- Stepping or scrubbing never re-executes the program and never replays raw
  events in JavaScript.

This keeps step/scrub semantics deterministic while allowing the browser to
drive animation timing.

### 4.3 Scene computation

`TimelineBuilder.build(submission, traceJson, ToneProvider)` produces every
frame in one pass:

- Active file and line
- Call stack by replaying `METHOD_CALL` / `METHOD_RETURN`
- Visible variables by replaying `ASSIGNMENT` events
- Array structures, changed indices, and cell geometry
- Visible stdout (shown on the final frame)
- Active event and mechanical label
- `visible_files` (currently one active file in the renderer)
- Tone spec and animation hint

The builder caps output at `MAX_FRAMES` and `MAX_JSON_CHARS`; on overflow it
returns an error timeline with sources and zero frames.

### 4.4 Labels

`TimelineBuilder` emits literal, mechanically derived text for the v1 events:

- `METHOD_CALL`: `Sample.add(2, 3)`
- `METHOD_RETURN`: `Sample.add → 5`
- `ASSIGNMENT`: `x: 1 → 2` or `x = 2` on first sight

Comparison, branch, loop, and cross-file labels remain reserved for the future
event types already present in `EventType`.

### 4.5 Data structures and layout

Arrays are implemented from the formatter's `[1, 2, 3]` output:

- `StructureState` carries elements and changed indices.
- Java computes fixed-width cell geometry (`x`, `y`, `w`, `h`).
- The browser absolutely positions cells from that geometry; it does not decide
  where nodes go.
- Empty arrays stay in the variable panel but do not create an empty structure
  canvas.

Lists, trees, stacks, and queues remain future work.

### 4.6 Audio

`ToneProvider` is the framework-free boundary between the visualizer package and
the deterministic hash:

- `RunController` implements it with `HashFunction.toneFor(...)` and
  `HashFunction.hashHex(...)`.
- `TimelineBuilder` calls it once per frame and stores the full `ToneSpec`
  (`signature`, `frequency_hz`, `oscillator`, `duration_ms`, `note_index`,
  `midi_note`, `hash`).
- The browser only feeds those fields into `OscillatorNode.type`,
  `OscillatorNode.frequency`, and the gain envelope; it contains no hash or
  mapping logic.
- Audio is unlocked on the first Play gesture and scheduled on the audio context
  clock. The audio toggle mutes/unmutes playback.

---

## 5. Browser renderer

The player lives in `src/main/resources/static/submit.html`; its styles are in
`assets/submit.css`. It is intentionally inline and small rather than a separate
frontend app.

Renderer responsibilities:

- Load `trace.timeline` from the run response.
- Paint the active source file and line highlight from `active_file` /
  `active_line`.
- Paint call stack, variables, stdout, and event list from the current frame.
- Paint structures from Java-provided geometry.
- Apply CSS transitions using `animation.kind` and `animation.duration_ms`.
- Play `tone` on frame changes when audio is enabled.
- Move only the cursor for play/pause/step/scrub/speed.
- Show status, empty states, and classified errors from timeline fields.

Keep out of the renderer:

- Call-stack reconstruction from raw events
- Variable replay and value diffing
- Event interpretation or label generation
- Any code that derives scene state from the trace

The renderer remains replaceable by a TeaVM-compiled Java renderer later without
changing the timeline contract. Simultaneous multi-file panes and connector
lines are not implemented in v1; the fields exist in the schema for that work.

---

## 6. Multi-file and cross-file behavior

This is a frontend/backend split:

- **Module A** must emit `CROSS_FILE_ACCESS` events (and ideally method-call
  targets that identify the destination file).
- **Module B** (now the Java core + renderer) uses those events to:
  - Show the relevant files simultaneously (`visible_files`)
  - Draw a connector from the accessing block to the source block
  - Highlight the accessed member/file in the target pane

Until `CROSS_FILE_ACCESS` exists, the timeline can fall back to `event.file` and
show one file at a time. The multi-file layout and connector schema can be built
now and populated once the trace events are available.

Data structures depend on Module A too:

- Arrays can be reconstructed from `ASSIGNMENT` values today (the formatter
  prints `[1, 2, 3]`).
- Lists/trees/objects need `DataStructureState` snapshots or richer value
  formatting. Define the frame schema now; degrade gracefully until then.

---

## 7. Fallback: TeaVM (strict "UI code must be Java")

If a reviewer requires the browser UI code itself to be Java:

- Add `visualizer-teavm/` (or `visualizer-web-java/`) that compiles
  `visualizer-core` plus a Java browser layer to JavaScript.
- Use TeaVM's DOM/Canvas/WebAudio bindings for rendering and audio.
- Export one entry point (`Visualizer.start(timelineJson)`) and load the
  generated bundle from the player page.
- Keep the same `VisualizationTimeline` contract; only the renderer changes.
- Do **not** try to compile JavaFX with TeaVM. JavaFX is not supported.
- Do not use `javax.sound.sampled`; use Web Audio bindings instead.
- Watch for TeaVM limitations: reflection, threads/executors, Jackson, and
  `java.awt`. The core must stay plain Java and framework-free.

This is a fallback, not the first implementation. The server-timeline approach
gets the same Java core working with much less build risk.

### Rejected alternatives

- **JavaFX in the browser**: not supported by browsers.
- **Applets / Java Web Start**: removed from modern browsers.
- **CheerpJ**: runs some Java in the browser but not JavaFX; heavy and
  proprietary.
- **Vaadin Flow**: Java UI on Spring Boot is possible, but it adds a large
  framework, server round trips, and a different UI model. It is not a good fit
  for smooth per-event animation.
- **Server-side SVG snapshot per request**: Java-centric but too chatty and
  laggy for playback. Precomputed timeline + client interpolation is better.

---

## 8. Milestones

### M0 — Freeze the interpretation and contract — **done**
- Confirmed: Java timeline + thin JS/CSS/Web Audio renderer.
- Froze timeline schema v1 and source delivery inside the timeline.
- Result: `/submit` hosts the player; no separate frontend or desktop client.

### M1 — Pure Java core — **done**
- Added `visualizer/SceneState.java`, `TimelineBuilder.java`, `ToneSpec.java`,
  and `ToneProvider.java`.
- Reused `TraceEvent`, `TraceSerializer`, `Json`, and `HashFunction`; no JavaFX.
- JUnit coverage in `TimelineBuilderTest`: trace parsing, stack replay, variable
  replay, array parsing/layout, labels, tone signatures, empty/error timelines,
  and additive attachment.
- Acceptance met: `mvn test` passes without a UI dependency in the core.

### M2 — Timeline builder + server integration — **done**
- `TimelineBuilder` computes frames, geometry, labels, stdout visibility, and tone
  specs in one pass.
- `RunController` attaches the timeline as an additive sibling of the frozen trace
  JSON; classified failures also get a trace-shaped body and zero-frame timeline.
- Caps: `MAX_FRAMES = 8000`, `MAX_JSON_CHARS = 4_000_000`. Oversize timelines
  return `TIMELINE_TOO_LARGE` while preserving the raw trace.
- `TimelineBuilderTest` and `WebLayerTest` cover completed and error paths.

### M3 — Thin renderer — **done**
- Replaced the old trace report on `submit.html` with the timeline player.
- Renders active file/line, call stack, variables, arrays, stdout, event list, and
  raw JSON.
- Play/pause, rewind/end, step, scrub, and speed move only the browser cursor.
- All scene state comes from timeline frames; JavaScript performs no event replay.

### M4 — Audio — **done**
- `RunController` wires `HashFunction` into the core through `ToneProvider`.
- Every frame carries a `ToneSpec`; the browser schedules it with Web Audio.
- Audio unlocks on the first user gesture and can be toggled at any time.
- The same event signature yields the same tone because the mapping still comes
  from `HashFunction` / `test-vectors.json`.

### M5 — Data structures — **arrays done, lists/trees pending**
- Arrays are extracted from `ASSIGNMENT` values, laid out in Java, and rendered
  with changed-cell highlighting.
- Empty arrays stay in the variable panel without creating an empty canvas.
- Lists, trees, objects, stacks, and queues remain future work.

### M6 — Multi-file + connector lines — **partial**
- Timeline sources include every submitted file with content, and the player can
  switch file tabs.
- `visible_files` and `connectors` exist in the schema; `connectors` is empty and
  simultaneous multi-file panes are not implemented.
- Depends on `CROSS_FILE_ACCESS` events from Module A for meaningful connectors.

### M7 — Polish — **partial**
- Done: dark mode, responsive layout, keyboard transport shortcuts, classified
  error messaging, empty states, raw-JSON copy, and frame caps.
- Pending: labels for comparison/branch/loop/cross-file events and deeper
  large-trace performance work.

### M8 — Optional TeaVM spike — **not started**
- Only needed if the UI code itself must be Java.
- Compile a minimal core + Java DOM renderer to JS and compare bundle size,
  startup time, and debugging against the thin renderer.

---

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Timeline too large | `MAX_FRAMES` / `MAX_JSON_CHARS` caps return an explicit `TIMELINE_TOO_LARGE` timeline while the raw trace stays available; keyframes/diffs are future work |
| Animation feels laggy | Precompute geometry/keyframes in Java; interpolate in the browser; never round-trip per frame |
| Logic drifts between Java and JS | Keep JS dumb; all state comes from frames; test the timeline, not the renderer |
| TeaVM cannot compile a dependency | Keep core framework-free; avoid JavaFX, Jackson, javax.sound, threads |
| Missing trace events (branch/loop/cross-file) | Build the frame schema now; fall back gracefully; coordinate with Module A |
| Source not available in the player | Include source content in the timeline or serve it from the server; do not rely on localStorage |
| Audio autoplay policy | Unlock AudioContext on first Play; schedule with `currentTime` |
| Timeline privacy | Keep source/trace ephemeral; do not log source; enforce retention |
| Browser differences | Test Chrome, Firefox, Safari; use standard SVG/Web Audio |

---

## 10. What stays unchanged

- `RunnerEngine`, `DockerSandbox`, `TracerMain`, `TraceRecorder`, and the
  versioned trace schema.
- `HashFunction` semantics and `test-vectors.json`; the core reaches it through
  `ToneProvider`, so there is only one mapping implementation.
- `POST /api/run`'s existing trace fields (the timeline is an additive sibling).
- `GET /hash`.
- The submit page's editor, sandbox, and limit behavior.
- The browser remains the only renderer; there is no separate desktop client.

---

## 11. Documentation status

- ADR-6 / ADR-11 still describe the earlier desktop/TypeScript framing in
  `Project Specification/`; they need an amendment to record the implemented
  Java timeline + thin web renderer decision.
- The SRS module split should note that Module B/C logic now runs in the Java
  core; the browser is presentation only.
- `README.md`, `ARCHITECTURE.md`, `docs/HashFunctionManual.md`, and the dataflow
  diagrams now describe the timeline and player; keep them in sync with future
  schema changes.
- This file remains the implementation plan and status record.

---

## 12. Next steps

1. Amend ADR-6/ADR-11 and the SRS module split to match the implemented split.
2. Add list/tree/object state extraction and layouts to `TimelineBuilder`.
3. Add `CROSS_FILE_ACCESS` trace events, then simultaneous file panes and
   connector rendering.
4. Add labels for comparison, branch, loop, and cross-file events.
5. Continue large-trace performance work (keyframes/diffs, frame caching).
6. If required, spike TeaVM with a minimal Java renderer.
7. Add a multi-file end-to-end test once the UI supports simultaneous panes.

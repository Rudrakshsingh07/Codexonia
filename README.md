# Codexonia

A web application that compiles, runs, and traces working Java code inside a
sandboxed backend, returning a structured execution trace.

## Pages

| Route | Page |
| --- | --- |
| `/` | Landing page with the animated trace console |
| `/submit` | Java editor and Java-authored timeline player |
| `/docs` | Field guide: trace contract, limits, audio mapping, HTTP API |
| `/about` | Project rationale, architecture, and roadmap |
| `/privacy` | Data handling and sandbox retention note |
| `/terms` | Plain-language terms of use |
| unknown routes | Custom `404` page (`/error/404.html`) |
| server errors | Custom `500` page (`/error/500.html`) |

`PageController` maps the clean URLs to the static HTML files so the browser
navigation does not need `.html` suffixes. Both `/docs` and `/docs/` work.

## Repository layout

```
src/main/java/            Java backend sources (Module A: validation/execution/tracing,
                          plus the deterministic hash/tone function — ADR-2)
  PageController.java     Clean-URL forwards for the static site
  HashFunction.java       FNV-1a 32-bit signature hash → deterministic tone parameters
  HashController.java     Spring endpoint exposing the hash/tone mapping
  runner/                 Validation, Docker sandbox, JDI tracer, trace serialization
  visualizer/             TimelineBuilder, SceneState, ToneSpec/ToneProvider — the
                          Java-authored replay model the browser only paints
src/main/resources/static/
  index.html              Landing page (hero trace console, pipeline, event anatomy)
  submit.html             Editor, run flow, timeline player, compile-error panel
  docs.html               Field guide
  about.html              Project and architecture
  privacy.html            Privacy note
  terms.html              Terms of use
  error/404.html          Custom not-found page
  error/500.html          Custom server-error page
  favicon.ico             Browser icon fallback
  assets/                 Shared shell (site.css/site.js), compiled Tailwind CSS,
                          self-hosted fonts, page CSS, landing animation, logo,
                          CodeMirror vendor files, and theme bootstrap
src/test/java/            Controller/route tests, timeline-builder tests, and a
                          live-server error-page test
docs/                     Developer-facing docs (HashFunctionManual.md,
                          RunnerEngineManual.md, JavaVisualizerPlan.md)
Project Specification/    PRD, SRS, and ADR log
Diagrams/                 UML / sequence / use-case diagrams, plus
                          Code-to-HashFunction-Dataflow.* (submission → trace + timeline → tone)
Study/                    Obsidian study notes (git-ignored)
test-vectors.json         Shared signature → hash → tone vectors (ADR-2 cross-language contract)
```

## Hash function quick start

```bash
javac -d out src/main/java/HashFunction.java
java -cp out HashFunction            # verify shared test vectors
java -cp out HashFunction swap       # hash + tone for one signature
```

## Runner engine quick start

```bash
javac --add-modules jdk.jdi -d out src/main/java/runner/*.java src/main/java/runner/tracer/*.java
docker build -t codexonia-sandbox:latest -f sandbox/Dockerfile .
java --add-modules jdk.jdi -cp out runner.RunnerEngine examples/Sample.java out.json.gz
java --add-modules jdk.jdi -cp out runner.SelfTest --docker   # end-to-end check
```

Compiles and runs a single `.java` file inside a hardened Docker sandbox and
emits a versioned event trace (`METHOD_CALL`, `ASSIGNMENT`, `METHOD_RETURN` in
v1). See `docs/RunnerEngineManual.md`. Validation errors, timeouts, output
caps, and runtime errors are classified, not swallowed.

See `docs/HashFunctionManual.md` for the full contract handed to the audio
engine developer.

## Web UI quick start

```bash
./run.sh
```

`run.sh` builds the sandbox image if needed, starts the Spring Boot app
(Tomcat, port 8080), and opens the landing page. Flow:

1. Start at `/` for the product tour, or go straight to `/submit`.
2. Edit Java in the editor and click **Run & trace**.
3. The backend traces execution (`POST /api/run`) via the Docker sandbox and
   attaches a Java-authored visualization timeline to the trace response.
4. The timeline player renders the active line, call stack, variables, array
   layout, stdout, and event stream. Play, pause, step, scrub, speed, and the
   audio toggle all drive the browser cursor; scene contents and tone specs come
   from the Java frames. Compiler and classification errors stay in the player
   and the error panel without discarding the editor buffer.

The browser is a thin renderer. Call-stack replay, variable state, labels, array
geometry, and tone parameters are computed in `visualizer/TimelineBuilder`; the
browser only paints frames and drives playback. The design and remaining
extensions (lists/trees, cross-file connectors, TeaVM fallback) are described in
`docs/JavaVisualizerPlan.md`.

Fonts and CodeMirror are self-hosted, and Tailwind is compiled ahead of time
instead of loading the Play CDN. If you add new utility classes to the markup
or JS, regenerate the stylesheet:

```bash
npx tailwindcss@3.4.17 -c tailwind.config.js -i tailwind.input.css -o src/main/resources/static/assets/tailwind.css --minify
```

`GET /hash?input=...` exposes the deterministic hash → tone mapping.

## Tests

```bash
"$HOME/tools/apache-maven-3.9.9/bin/mvn" test
```

`WebLayerTest` covers clean URLs, trailing slashes, static assets, hash output,
timeline attachment, and classified run errors. `TimelineBuilderTest` covers
frame replay, labels, array geometry, tone signatures, and empty/error timelines.
`ErrorPageIntegrationTest` starts a real servlet container and verifies the
custom HTML `404` page and JSON error responses.

## Docker

```bash
docker build -t codexonia .
docker run --rm codexonia
```

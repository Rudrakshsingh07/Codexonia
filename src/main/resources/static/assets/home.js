(function () {
  "use strict";

  var CODE = [
    '<span class="tok-key">public class</span> <span class="tok-type">Trace</span> {',
    '    <span class="tok-key">static</span> <span class="tok-type">int</span> <span class="tok-fn">sum</span>(<span class="tok-type">int</span>[] a) {',
    '        <span class="tok-type">int</span> total = <span class="tok-num">0</span>;',
    '        <span class="tok-key">for</span> (<span class="tok-type">int</span> n : a) {',
    '            total += n;',
    '        }',
    '        <span class="tok-key">return</span> total;',
    '    }',
    '',
    '    <span class="tok-key">public static void</span> <span class="tok-fn">main</span>(<span class="tok-type">String</span>[] args) {',
    '        <span class="tok-type">int</span>[] data = {<span class="tok-num">4</span>, <span class="tok-num">9</span>, <span class="tok-num">2</span>};',
    '        <span class="tok-type">int</span> result = <span class="tok-fn">sum</span>(data);',
    '        System.out.<span class="tok-fn">println</span>(result);',
    '    }',
    '}'
  ];

  var FRAMES = [
    {
      line: 10,
      type: "METHOD_CALL",
      title: "Trace.main([])",
      sub: "entered at line 10",
      stack: [{ name: "main", line: 10 }],
      vars: {},
      array: null,
      arrayActive: [],
      changed: [],
      stdout: ""
    },
    {
      line: 11,
      type: "ASSIGNMENT",
      title: "data = [4, 9, 2]",
      sub: "new int[] · 3 elements",
      stack: [{ name: "main", line: 11 }],
      vars: { data: "[4, 9, 2]" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: ["data"],
      stdout: ""
    },
    {
      line: 12,
      type: "METHOD_CALL",
      title: "sum(data)",
      sub: "argument · int[] {4, 9, 2}",
      stack: [{ name: "main", line: 12 }, { name: "sum", line: 2 }],
      vars: { data: "[4, 9, 2]" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: [],
      stdout: ""
    },
    {
      line: 3,
      type: "ASSIGNMENT",
      title: "total = 0",
      sub: "new local · int",
      stack: [{ name: "main", line: 12 }, { name: "sum", line: 3 }],
      vars: { data: "[4, 9, 2]", total: "0" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: ["total"],
      stdout: ""
    },
    {
      line: 5,
      type: "ASSIGNMENT",
      title: "total = 4",
      sub: "0 → 4 · n = 4",
      stack: [{ name: "main", line: 12 }, { name: "sum", line: 5 }],
      vars: { data: "[4, 9, 2]", total: "4" },
      array: [4, 9, 2],
      arrayActive: [0],
      changed: ["total"],
      stdout: ""
    },
    {
      line: 5,
      type: "ASSIGNMENT",
      title: "total = 13",
      sub: "4 → 13 · n = 9",
      stack: [{ name: "main", line: 12 }, { name: "sum", line: 5 }],
      vars: { data: "[4, 9, 2]", total: "13" },
      array: [4, 9, 2],
      arrayActive: [1],
      changed: ["total"],
      stdout: ""
    },
    {
      line: 5,
      type: "ASSIGNMENT",
      title: "total = 15",
      sub: "13 → 15 · n = 2",
      stack: [{ name: "main", line: 12 }, { name: "sum", line: 5 }],
      vars: { data: "[4, 9, 2]", total: "15" },
      array: [4, 9, 2],
      arrayActive: [2],
      changed: ["total"],
      stdout: ""
    },
    {
      line: 7,
      type: "METHOD_RETURN",
      title: "sum → 15",
      sub: "returns int",
      stack: [{ name: "main", line: 12 }, { name: "sum", line: 7 }],
      vars: { data: "[4, 9, 2]", total: "15" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: [],
      stdout: ""
    },
    {
      line: 12,
      type: "ASSIGNMENT",
      title: "result = 15",
      sub: "new local · int",
      stack: [{ name: "main", line: 12 }],
      vars: { data: "[4, 9, 2]", result: "15" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: ["result"],
      stdout: ""
    },
    {
      line: 13,
      type: "METHOD_CALL",
      title: "println(result)",
      sub: "argument · 15",
      stack: [{ name: "main", line: 13 }],
      vars: { data: "[4, 9, 2]", result: "15" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: [],
      stdout: "15\n"
    },
    {
      line: 14,
      type: "METHOD_RETURN",
      title: "main → void",
      sub: "program complete",
      stack: [{ name: "main", line: 14 }],
      vars: { data: "[4, 9, 2]", result: "15" },
      array: [4, 9, 2],
      arrayActive: [],
      changed: [],
      stdout: "15\n"
    }
  ];

  var TYPE_CLASS = {
    METHOD_CALL: "is-call",
    ASSIGNMENT: "is-assignment",
    METHOD_RETURN: "is-return"
  };

  function ready() {
    var root = document.getElementById("hero-demo");
    if (!root) return;

    var codeEl = document.getElementById("demo-code");
    var stepEl = document.getElementById("demo-step");
    var eventTypeEl = document.getElementById("demo-event-type");
    var titleEl = document.getElementById("demo-event-title");
    var subEl = document.getElementById("demo-event-sub");
    var depthEl = document.getElementById("demo-depth");
    var stackEl = document.getElementById("demo-stack");
    var varsEl = document.getElementById("demo-vars");
    var arrayEl = document.getElementById("demo-array");
    var stdoutEl = document.getElementById("demo-stdout");
    var railLabelEl = document.getElementById("demo-rail-label");
    var ticksEl = document.getElementById("demo-ticks");
    var toggleEl = document.getElementById("demo-toggle");
    var toggleIcon = toggleEl ? toggleEl.querySelector(".material-symbols-outlined") : null;

    if (!codeEl || !ticksEl) return;

    codeEl.innerHTML = CODE.map(function (line, index) {
      return '<div class="code-line" data-line="' + (index + 1) + '">' +
        '<span class="line-no">' + (index + 1) + "</span>" +
        "<span>" + (line || " ") + "</span>" +
      "</div>";
    }).join("");

    ticksEl.innerHTML = FRAMES.map(function () {
      return '<span class="rail-tick"></span>';
    }).join("");

    var codeLines = Array.prototype.slice.call(codeEl.querySelectorAll(".code-line"));
    var ticks = Array.prototype.slice.call(ticksEl.querySelectorAll(".rail-tick"));
    var reduced = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var index = 0;
    var timer = null;
    var playing = !reduced;

    function render(frameIndex) {
      index = frameIndex;
      var frame = FRAMES[frameIndex];

      codeLines.forEach(function (line) {
        line.classList.toggle("is-active", Number(line.dataset.line) === frame.line);
      });

      if (stepEl) {
        stepEl.textContent = String(frameIndex + 1).padStart(2, "0") + " / " + FRAMES.length;
      }

      if (eventTypeEl) {
        eventTypeEl.textContent = frame.type;
        eventTypeEl.className = "event-chip " + (TYPE_CLASS[frame.type] || "is-assignment");
      }

      if (titleEl) titleEl.textContent = frame.title;
      if (subEl) subEl.textContent = frame.sub;

      if (depthEl) {
        depthEl.textContent = frame.stack.length + (frame.stack.length === 1 ? " frame" : " frames");
      }

      if (stackEl) {
        stackEl.innerHTML = frame.stack.slice().reverse().map(function (entry, position) {
          return '<div class="stack-row"><span>' + entry.name + "()</span>" +
            '<span class="stack-tag">' + (position === 0 ? "TOP" : "L" + entry.line) + "</span></div>";
        }).join("");
      }

      if (varsEl) {
        var keys = Object.keys(frame.vars);
        varsEl.innerHTML = keys.length
          ? keys.map(function (key) {
              return '<div class="var-row' + (frame.changed.indexOf(key) >= 0 ? " is-changed" : "") + '">' +
                '<span class="var-name">' + key + "</span>" +
                '<span class="var-value">' + frame.vars[key] + "</span>" +
              "</div>";
            }).join("")
          : '<div class="var-row"><span class="var-name">—</span><span class="var-value">no locals yet</span></div>';
      }

      if (arrayEl) {
        arrayEl.innerHTML = frame.array
          ? frame.array.map(function (value, cellIndex) {
              return '<span class="array-cell' +
                (frame.arrayActive.indexOf(cellIndex) >= 0 ? " is-active" : "") + '">' + value + "</span>";
            }).join("")
          : '<span class="array-cell">—</span>';
      }

      if (stdoutEl) stdoutEl.textContent = frame.stdout || "—";

      if (railLabelEl) {
        railLabelEl.textContent = frame.type + " · line " + frame.line;
      }

      ticks.forEach(function (tick, tickIndex) {
        tick.classList.toggle("is-past", tickIndex < frameIndex);
        tick.classList.toggle("is-current", tickIndex === frameIndex);
      });

      if (toggleIcon) {
        toggleIcon.textContent = playing ? "pause" : "play_arrow";
      }
      if (toggleEl) {
        toggleEl.setAttribute("aria-label", playing ? "Pause animation" : "Play animation");
      }
    }

    function stop() {
      if (timer) {
        window.clearInterval(timer);
        timer = null;
      }
    }

    function start() {
      stop();
      if (reduced) return;
      playing = true;
      if (toggleIcon) toggleIcon.textContent = "pause";
      if (toggleEl) toggleEl.setAttribute("aria-label", "Pause animation");
      timer = window.setInterval(function () {
        render((index + 1) % FRAMES.length);
      }, 1650);
    }

    function pause() {
      playing = false;
      stop();
      if (toggleIcon) toggleIcon.textContent = "play_arrow";
      if (toggleEl) toggleEl.setAttribute("aria-label", "Play animation");
    }

    if (toggleEl) {
      toggleEl.addEventListener("click", function () {
        if (playing) {
          pause();
        } else {
          playing = true;
          start();
        }
      });
    }

    document.addEventListener("visibilitychange", function () {
      if (document.hidden) {
        stop();
      } else if (playing) {
        start();
      }
    });

    render(reduced ? FRAMES.length - 1 : 0);
    if (playing) start();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", ready);
  } else {
    ready();
  }
})();

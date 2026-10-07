// visualize.js — reads the steps produced by the backend (via analyze.js)
// and renders bars, table and sound. It never generates step data itself.

const barsContainer = document.getElementById("barsContainer");
const stepsTableBody = document.getElementById("stepsTableBody");
const stepPosEl = document.getElementById("stepPos");
const stepTotalEl = document.getElementById("stepTotal");
const playBtn = document.getElementById("playBtn");
const stepBackBtn = document.getElementById("stepBackBtn");
const stepFwdBtn = document.getElementById("stepFwdBtn");
const speedSlider = document.getElementById("speedSlider");
const speedVal = document.getElementById("speedVal");
const emptyState = document.getElementById("emptyState");
const vizContent = document.getElementById("vizContent");

let steps = [];
let array = [];
let currentIndex = -1;
let playing = false;
let playTimer = null;

function load() {
  const rawSteps = sessionStorage.getItem("stepsonic_steps");
  const rawArray = sessionStorage.getItem("stepsonic_array");

  if (!rawSteps) {
    emptyState.style.display = "block";
    vizContent.style.display = "none";
    return;
  }

  steps = JSON.parse(rawSteps);
  array = rawArray ? JSON.parse(rawArray) : [];
  stepTotalEl.textContent = steps.length;

  renderBars();
  renderTable();
}

function renderBars() {
  barsContainer.innerHTML = "";
  const max = Math.max(1, ...array.map(v => Math.abs(v)));
  array.forEach((value) => {
    const bar = document.createElement("div");
    bar.className = "bar";
    bar.style.height = `${Math.max(4, (Math.abs(value) / max) * 100)}%`;
    bar.dataset.value = value;
    barsContainer.appendChild(bar);
  });
}

function renderTable() {
  stepsTableBody.innerHTML = "";
  steps.forEach((s, i) => {
    const tr = document.createElement("tr");
    tr.id = `step-row-${i}`;
    tr.innerHTML = `
      <td>${s.stepNo ?? i + 1}</td>
      <td><span class="badge ${s.type}">${s.type}</span></td>
      <td>${s.line ?? "—"}</td>
      <td class="mono">${escapeHtml(s.snippet ?? "")}</td>
      <td>${s.frequencyHz ? s.frequencyHz.toFixed(2) : "—"}</td>
    `;
    stepsTableBody.appendChild(tr);
  });
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

function highlightStep(index) {
  document.querySelectorAll("table.steps tr.current")
    .forEach(el => el.classList.remove("current"));
  const row = document.getElementById(`step-row-${index}`);
  if (row) {
    row.classList.add("current");
    row.scrollIntoView({ block: "nearest" });
  }
  stepPosEl.textContent = index + 1;

  // Highlight bars touched by this step, if the backend included indices.
  document.querySelectorAll(".bar").forEach(b => b.classList.remove("compare", "swap", "sorted"));
  const step = steps[index];
  if (step && Array.isArray(step.indices)) {
    const cls = step.type === "SWAP" ? "swap" : step.type === "COMPARE" ? "compare" : null;
    if (cls) {
      step.indices.forEach(idx => {
        const bar = barsContainer.children[idx];
        if (bar) bar.classList.add(cls);
      });
    }
  }
}

function playStep(index) {
  if (index < 0 || index >= steps.length) return;
  currentIndex = index;
  highlightStep(index);
  playTone(steps[index].frequencyHz);
}

function stepForward() {
  if (currentIndex + 1 < steps.length) playStep(currentIndex + 1);
  else stopPlayback();
}

function stepBackward() {
  if (currentIndex - 1 >= 0) playStep(currentIndex - 1);
}

function startPlayback() {
  if (steps.length === 0) return;
  playing = true;
  playBtn.textContent = "⏸ Pause";
  const speed = parseFloat(speedSlider.value);
  const interval = 400 / speed;
  playTimer = setInterval(() => {
    if (currentIndex + 1 >= steps.length) {
      stopPlayback();
      return;
    }
    stepForward();
  }, interval);
}

function stopPlayback() {
  playing = false;
  playBtn.textContent = "▶ Play";
  clearInterval(playTimer);
}

playBtn.addEventListener("click", () => {
  if (playing) stopPlayback();
  else startPlayback();
});
stepFwdBtn.addEventListener("click", () => { stopPlayback(); stepForward(); });
stepBackBtn.addEventListener("click", () => { stopPlayback(); stepBackward(); });
speedSlider.addEventListener("input", () => {
  speedVal.textContent = `${speedSlider.value}x`;
  if (playing) { stopPlayback(); startPlayback(); }
});

load();

// analyze.js — collects input and calls the backend. All analysis
// (step detection, frequency assignment) happens server-side in Java.

const analyzeBtn = document.getElementById("analyzeBtn");
const errorBanner = document.getElementById("errorBanner");
const codeInput = document.getElementById("codeInput");
const fileInput = document.getElementById("fileInput");
const algoSelect = document.getElementById("algoSelect");
const arrayInput = document.getElementById("arrayInput");
const statusDot = document.getElementById("backendStatus");
const statusText = document.getElementById("statusText");

function showError(message) {
  errorBanner.textContent = message;
  errorBanner.classList.add("show");
}

function clearError() {
  errorBanner.classList.remove("show");
  errorBanner.textContent = "";
}

// Load a .java file into the textarea instead of uploading it separately —
// the backend only needs the code as text.
fileInput.addEventListener("change", () => {
  const file = fileInput.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (e) => { codeInput.value = e.target.result; };
  reader.readAsText(file);
});

function parseInputArray(raw) {
  if (!raw || !raw.trim()) return [];
  return raw.split(",").map(s => Number(s.trim())).filter(n => !Number.isNaN(n));
}

async function runAnalysis() {
  clearError();
  const code = codeInput.value.trim();
  const algorithm = algoSelect.value;
  const input = parseInputArray(arrayInput.value);

  if (!code) {
    showError("Paste some code or upload a .java file first.");
    return;
  }

  analyzeBtn.disabled = true;
  analyzeBtn.textContent = "Analyzing…";

  try {
    const steps = await analyzeCode(code, algorithm, input);
    sessionStorage.setItem("stepsonic_steps", JSON.stringify(steps));
    sessionStorage.setItem("stepsonic_array", JSON.stringify(input));
    window.location.href = "visualize.html";
  } catch (err) {
    showError(err.message || "Analysis failed.");
  } finally {
    analyzeBtn.disabled = false;
    analyzeBtn.textContent = "Analyze & Sonify";
  }
}

async function refreshBackendStatus() {
  const online = await checkBackendHealth();
  statusDot.className = "status-dot " + (online ? "online" : "offline");
  statusText.textContent = online ? "Backend online" : "Backend offline";
  analyzeBtn.disabled = !online;
}

refreshBackendStatus();
setInterval(refreshBackendStatus, 15000);

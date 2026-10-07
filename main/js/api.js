// api.js — all calls to the Spring Boot backend live here.
// Nothing in this file invents step data; it only talks to /api/*.

const API_BASE = ""; // same-origin when served by Spring Boot; change if hosted separately

async function analyzeCode(code, algorithm, inputArray) {
  const res = await fetch(`${API_BASE}/api/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, algorithm, input: inputArray })
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body && body.error) message = body.error;
    } catch (_) { /* ignore parse errors */ }
    throw new Error(message);
  }

  return res.json(); // expected: [{ stepNo, type, line, snippet, frequencyHz }, ...]
}

async function checkBackendHealth() {
  try {
    const res = await fetch(`${API_BASE}/api/health`, { method: "GET" });
    if (!res.ok) return false;
    const body = await res.json();
    return body.status === "UP";
  } catch (_) {
    return false;
  }
}

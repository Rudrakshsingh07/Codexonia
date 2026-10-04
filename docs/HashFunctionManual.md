# HashFunction Manual — For the Audio Processing Engine Developer

This document defines the contract implemented by `HashFunction.java` (Java prototype).
The production version will run in TypeScript in the browser and must produce
**byte-for-byte identical results**. Refer to ADR-2 and the Tone Hash Function
design doc for background.

## 1. Purpose

Map a normalized **event signature** string to deterministic **tone parameters**:
frequency (Hz), oscillator type (timbre), and duration (ms). The same signature
must always yield the same parameters, on any platform, in any browser.

## 2. Input: the event signature

- A single string built from the event's **event type plus normalized, non-volatile
  attributes** — e.g. `compare:lt`, `compare:eq`, `branch:for-loop`, `swap`,
  `assignment`, `call`, `return`, `access:cross-file`.
- **ASCII only** (chars ≤ 0x7F). Reject anything else.
- **Normalize before hashing**: lowercase, no surrounding whitespace. `"swap"` and
  `"Swap"` hash differently and will produce different tones.
- Do NOT include volatile data (variable values, indices, sequence numbers) —
  otherwise the same *kind* of event would not map to the same tone (FR14).

Canonical signature examples are listed in `test-vectors.json` (`VECTOR_SIGNATURES`).

## 3. Stage 1 — FNV-1a 32-bit hash

```
hash = 0x811c9dc5            // FNV-1a 32-bit offset basis
for each char c of the signature (by UTF-16 code unit, i.e. charCodeAt):
    hash = hash XOR c
    hash = hash * 0x01000193 // FNV-1a 32-bit prime; must wrap at 32 bits
```

- Java: `int` wraps naturally.
- TypeScript: use `Math.imul(hash, 0x01000193)` and `>>> 0` to keep it unsigned.
  Do NOT use `*` alone (64-bit doubles lose exactness past 2^53) and do NOT use
  any built-in string hash.
- Treat the result as an **unsigned** 32-bit integer downstream.

## 4. Stage 2 — hash → tone parameters (integer math only)

```
u         = unsigned 32-bit hash
noteIndex = u % 7
oscIndex  = (u >>> 16) % 4
duration  = 150 + ((u >>> 24) % 4) * 50   // ms
```

Reference tables (must match exactly):

| noteIndex | MIDI note | frequency Hz |
|---|---|---|
| 0 | 57 | 220.000000 |
| 1 | 60 | 261.625565 |
| 2 | 62 | 293.664768 |
| 3 | 64 | 329.627557 |
| 4 | 67 | 391.995436 |
| 5 | 69 | 440.000000 |
| 6 | 72 | 523.251131 |

Frequency table is a fixed constant (Hz = 440 · 2^((midi−69)/12), rounded to 6 dp).
Do not recompute with `Math.pow` at runtime — table lookup only, so all
platforms agree exactly.

| oscIndex | oscillator |
|---|---|
| 0 | sine |
| 1 | triangle |
| 2 | square |
| 3 | sawtooth |

## 5. Output

```json
{"signature": "swap",
 "hash": "64ed874e",
 "noteIndex": 2, "midiNote": 62, "frequencyHz": 293.664768,
 "oscillator": "triangle", "durationMs": 150}
```

`hash` is the unsigned 32-bit value as 8 lowercase hex digits.

## 6. Verification

`test-vectors.json` contains the full expected `signature → hash → tone` mapping
for 9 canonical signatures. Your TypeScript implementation must reproduce every
entry exactly. Java reference:

```bash
cd Projects/Codexonia
javac -d out src/main/java/HashFunction.java
java -cp out HashFunction                 # verifies vectors, exits 1 on mismatch
java -cp out HashFunction swap            # prints hash + tone for a signature
java -cp out HashFunction --generate      # regenerates the vector file
```

In the browser, feed `oscillator` into `OscillatorNode.type`, `frequencyHz` into
`OscillatorNode.frequency.value`, and `durationMs` into the envelope/stop time,
scheduled on the `AudioContext` clock (not `setTimeout`). Call
`audioCtx.resume()` on the first user gesture (Play). Memoizing per-signature
results in a `Map` is allowed (pure function, not persisted storage).

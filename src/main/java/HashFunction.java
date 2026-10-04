import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Codexonia's deterministic event-signature hash (ADR-2).
 *
 * FNV-1a 32-bit over the signature string's UTF-16 code units.
 * Signatures are restricted to ASCII so this is unambiguous and
 * reproducible in both Java (prototype) and TypeScript (production).
 *
 * The 32-bit hash is sliced into tone parameters with integer math only:
 *   - noteIndex  = unsignedHash % NOTE_COUNT        (pitch)
 *   - oscIndex   = (unsignedHash >>> 16) % 4        (timbre)
 *   - durationMs = 150 + ((unsignedHash >>> 24) % 4) * 50
 */
public class HashFunction {

    public static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    public static final int FNV_PRIME = 0x01000193;

    /** Fixed A-minor pentatonic set as MIDI note numbers. */
    public static final int[] MIDI_NOTES = {57, 60, 62, 64, 67, 69, 72};

    /** Hz values for MIDI_NOTES, hz = 440 * 2^((midi-69)/12), rounded to 6 dp. */
    public static final double[] NOTE_HZ = {
        220.0, 261.625565, 293.664768, 329.627557, 391.995436, 440.0, 523.251131
    };

    public static final String[] OSCILLATORS = {"sine", "triangle", "square", "sawtooth"};

    public static final class ToneParameters {
        public final int noteIndex;
        public final int midiNote;
        public final double frequencyHz;
        public final String oscillator;
        public final int durationMs;

        ToneParameters(int noteIndex, int midiNote, double frequencyHz, String oscillator, int durationMs) {
            this.noteIndex = noteIndex;
            this.midiNote = midiNote;
            this.frequencyHz = frequencyHz;
            this.oscillator = oscillator;
            this.durationMs = durationMs;
        }

        public String toJson() {
            return String.format(java.util.Locale.ROOT,
                    "{\"noteIndex\":%d,\"midiNote\":%d,\"frequencyHz\":%.6f,\"oscillator\":\"%s\",\"durationMs\":%d}",
                    noteIndex, midiNote, frequencyHz, oscillator, durationMs);
        }

        @Override
        public String toString() {
            return toJson();
        }
    }

    /** FNV-1a 32-bit hash of the signature's UTF-16 code units. */
    public static int fnv1a32(String signature) {
        if (signature == null) {
            throw new IllegalArgumentException("signature must not be null");
        }
        for (int i = 0; i < signature.length(); i++) {
            if (signature.charAt(i) > 0x7F) {
                throw new IllegalArgumentException("signature must be ASCII: " + signature);
            }
        }
        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < signature.length(); i++) {
            hash ^= signature.charAt(i);
            hash *= FNV_PRIME; // int wraps mod 2^32, matching Math.imul semantics
        }
        return hash;
    }

    /** Deterministically map a signature to tone parameters. */
    public static ToneParameters toneFor(String signature) {
        int hash = fnv1a32(signature);
        long u = Integer.toUnsignedLong(hash);
        int noteIndex = (int) (u % NOTE_HZ.length);
        int oscIndex = (int) ((u >>> 16) % OSCILLATORS.length);
        int durationMs = 150 + (int) ((u >>> 24) % 4) * 50;
        return new ToneParameters(noteIndex, MIDI_NOTES[noteIndex], NOTE_HZ[noteIndex],
                OSCILLATORS[oscIndex], durationMs);
    }

    public static String hashHex(String signature) {
        return String.format("%08x", fnv1a32(signature));
    }

    // ---- Shared test vectors ---------------------------------------------

    public static String vectorLine(String signature) {
        ToneParameters t = toneFor(signature);
        return String.format(java.util.Locale.ROOT,
                "  {\"signature\": \"%s\", \"hash\": \"%s\", \"noteIndex\": %d, \"midiNote\": %d, \"frequencyHz\": %.6f, \"oscillator\": \"%s\", \"durationMs\": %d}",
                signature, hashHex(signature), t.noteIndex, t.midiNote, t.frequencyHz, t.oscillator, t.durationMs);
    }

    public static final java.util.List<String> VECTOR_SIGNATURES = java.util.Arrays.asList(
            "compare:lt", "compare:eq", "branch:for-loop", "branch:while-loop",
            "assignment", "swap", "call", "return", "access:cross-file");

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--generate")) {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < VECTOR_SIGNATURES.size(); i++) {
                sb.append(vectorLine(VECTOR_SIGNATURES.get(i)));
                sb.append(i == VECTOR_SIGNATURES.size() - 1 ? "\n" : ",\n");
            }
            sb.append("]\n");
            System.out.print(sb);
            return;
        } else if (args.length > 0) {
            for (String signature : args) {
                System.out.println(signature + "  hash=" + hashHex(signature) + "  " + toneFor(signature));
            }
            return;
        }

        // Default: verify the shared vectors against the implementation.
        String json = new String(Files.readAllBytes(Paths.get("test-vectors.json")), StandardCharsets.UTF_8);
        int failures = 0;
        for (String signature : VECTOR_SIGNATURES) {
            ToneParameters t = toneFor(signature);
            String expectedHash = json.contains("\"hash\": \"" + hashHex(signature) + "\"") ? "ok" : "MISMATCH";
            boolean lineOk = json.contains(vectorLine(signature));
            System.out.printf("%-20s hash=%s  %s%n", signature, hashHex(signature), lineOk ? "OK" : "MISMATCH");
            if (!lineOk) failures++;
        }
        if (failures > 0) {
            System.err.println(failures + " vector(s) mismatched test-vectors.json");
            System.exit(1);
        }
        System.out.println("All test vectors match.");
    }
}

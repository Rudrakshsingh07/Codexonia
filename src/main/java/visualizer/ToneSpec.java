package visualizer;

/** Java-computed tone specification for one timeline frame. */
public final class ToneSpec {
    public final String signature;
    public final double frequencyHz;
    public final String oscillator;
    public final int durationMs;
    public final int noteIndex;
    public final int midiNote;
    public final String hash;

    public ToneSpec(String signature, double frequencyHz, String oscillator, int durationMs,
                    int noteIndex, int midiNote, String hash) {
        this.signature = signature;
        this.frequencyHz = frequencyHz;
        this.oscillator = oscillator;
        this.durationMs = durationMs;
        this.noteIndex = noteIndex;
        this.midiNote = midiNote;
        this.hash = hash;
    }
}

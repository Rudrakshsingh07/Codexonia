package runner;

/** Result of a RunnerEngine.run call: either a trace or a classified failure. */
public final class RunOutcome {
    public final boolean ok;
    public final String status;
    public final String errorMessage;
    public final String traceJson;
    public final byte[] traceGz;
    public final int eventCount;

    private RunOutcome(boolean ok, String status, String errorMessage, String traceJson, byte[] traceGz, int eventCount) {
        this.ok = ok;
        this.status = status;
        this.errorMessage = errorMessage;
        this.traceJson = traceJson;
        this.traceGz = traceGz;
        this.eventCount = eventCount;
    }

    public static RunOutcome ok(String traceJson, byte[] traceGz, int eventCount) {
        return new RunOutcome(true, "COMPLETE", null, traceJson, traceGz, eventCount);
    }

    public static RunOutcome error(String status, String errorMessage) {
        return new RunOutcome(false, status, errorMessage, "", new byte[0], 0);
    }

    public static RunOutcome error(String status, String errorMessage, String traceJson, byte[] traceGz, int eventCount) {
        return new RunOutcome(false, status, errorMessage, traceJson, traceGz, eventCount);
    }

    public static RunOutcome limit(String errorMessage) {
        return new RunOutcome(false, "LIMIT_EXCEEDED", errorMessage, "", new byte[0], 0);
    }
}

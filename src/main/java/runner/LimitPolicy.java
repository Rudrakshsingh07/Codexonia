package runner;

/** Tunables that bound what a job may consume (PRD FR18 / ADR-10). */
public final class LimitPolicy {
    public final int memoryMb;
    public final double cpus;
    public final int maxProcesses;
    /** Hard wall-clock limit enforced both inside the tracer and by the host killing the container. */
    public final int wallTimeoutSeconds;
    public final int killGraceSeconds;
    /** Max recorded trace events before the job fails with EVENT_CAP. */
    public final int maxEvents;
    public final int maxStdoutBytes;
    public final int maxStderrBytes;
    public final int maxSourceBytes;
    public final int maxFiles;
    public final int tmpfsMb;
    public final String image;

    public LimitPolicy(int memoryMb, double cpus, int maxProcesses, int wallTimeoutSeconds,
                       int killGraceSeconds, int maxEvents, int maxStdoutBytes, int maxStderrBytes,
                       int maxSourceBytes, int maxFiles, int tmpfsMb, String image) {
        this.memoryMb = memoryMb;
        this.cpus = cpus;
        this.maxProcesses = maxProcesses;
        this.wallTimeoutSeconds = wallTimeoutSeconds;
        this.killGraceSeconds = killGraceSeconds;
        this.maxEvents = maxEvents;
        this.maxStdoutBytes = maxStdoutBytes;
        this.maxStderrBytes = maxStderrBytes;
        this.maxSourceBytes = maxSourceBytes;
        this.maxFiles = maxFiles;
        this.tmpfsMb = tmpfsMb;
        this.image = image;
    }

    public static LimitPolicy defaults() {
        return new LimitPolicy(
                512, 1.0, 64, 30, 5,
                20000, 64 * 1024, 64 * 1024,
                256 * 1024, 16, 32,
                "codexonia-sandbox:latest");
    }
}

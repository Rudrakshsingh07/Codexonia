package runner;

/**
 * Event kinds recorded by the runner engine's tracer (ADR-1).
 *
 * v1 implements METHOD_CALL, METHOD_RETURN, and ASSIGNMENT.
 * The rest are reserved so the trace schema can grow without
 * breaking consumers (PRD FR6).
 */
public enum EventType {
    METHOD_CALL,
    METHOD_RETURN,
    ASSIGNMENT,

    // Reserved for later iterations:
    COMPARISON,
    BRANCH,
    LOOP_ITERATION,
    CROSS_FILE_ACCESS
}

package jreact.scheduler;

/**
 * Task priority (React's scheduler priority levels). Lower ordinal = more urgent. A task's priority also fixes
 * its timeout: once that much time has passed the task counts as expired and is no longer postponed.
 */
public enum Priority {
    /** Runs first and never yields. */
    IMMEDIATE(-1),
    /** Direct input: clicks, typing. */
    USER_BLOCKING(250),
    NORMAL(5_000),
    /** Transitions, background updates. Rendering at this level is time-sliced. */
    LOW(10_000),
    /** Effectively never expires. */
    IDLE(1_073_741_823);

    private final long timeoutMs;

    Priority(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public long timeoutMs() { return timeoutMs; }

    public boolean isMoreUrgentThan(Priority other) { return ordinal() < other.ordinal(); }

    /** Work at this priority may be split across several slices and yield to the host. */
    public boolean isTimeSliced() { return ordinal() >= LOW.ordinal(); }

    /** The more urgent of two priorities, either may be null. */
    public static Priority mostUrgent(Priority a, Priority b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.isMoreUrgentThan(a) ? b : a;
    }
}

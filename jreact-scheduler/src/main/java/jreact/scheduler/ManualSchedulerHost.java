package jreact.scheduler;

/**
 * A host with a virtual clock and no threads: nothing runs until you call {@link #step()} or
 * {@link #runUntilIdle()}. For tests and headless use. Time moves only through {@link #advance(long)},
 * so slicing and timeouts are fully deterministic.
 */
public final class ManualSchedulerHost implements SchedulerHost {

    private long clock;
    private Runnable callback;
    private Runnable timeout;
    private long timeoutAt;
    private int macrotasks;

    @Override public long now() { return clock; }

    /** Simulates elapsed time (work done by a task, or waiting between tasks). Never fires anything by itself. */
    public void advance(long ms) { clock += ms; }

    @Override public void requestHostCallback(Runnable work) { callback = work; }

    @Override public void requestHostTimeout(Runnable work, long delayMs) { timeout = work; timeoutAt = clock + delayMs; }

    @Override public void cancelHostTimeout() { timeout = null; }

    /** Runs one macrotask: a due timeout first, else the pending callback. Returns false if there was nothing to run. */
    public boolean step() {
        Runnable r;
        if (timeout != null && timeoutAt <= clock) { r = timeout; timeout = null; }
        else if (callback != null) { r = callback; callback = null; }
        else return false;
        macrotasks++;
        r.run();
        return true;
    }

    /** Runs macrotasks until no callback is pending. Timeouts that are not yet due stay pending. */
    public void runUntilIdle() { while (step()) { /* keep going */ } }

    /** True if a callback or timeout is waiting. */
    public boolean hasPending() { return callback != null || timeout != null; }

    /** Number of macrotasks run so far (one per {@link #step()}). */
    public int macrotasks() { return macrotasks; }
}

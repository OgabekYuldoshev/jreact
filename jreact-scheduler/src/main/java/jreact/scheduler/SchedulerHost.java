package jreact.scheduler;

/**
 * What the scheduler needs from the platform: a clock and a way to run code "later" without blocking
 * (browser: MessageChannel and setTimeout, tests: {@link ManualSchedulerHost}).
 */
public interface SchedulerHost {

    /** Monotonic time in milliseconds. */
    long now();

    /** Calls {@code work} once, soon, in a fresh macrotask so the platform can paint and handle input first. */
    void requestHostCallback(Runnable work);

    /** Calls {@code work} once after {@code delayMs}. At most one timeout is pending at a time. */
    void requestHostTimeout(Runnable work, long delayMs);

    void cancelHostTimeout();
}

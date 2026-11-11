package jreact.dom;

/** The browser's event loop primitives (TeaVM: performance.now, MessageChannel, setTimeout). */
public interface Timers {
    /** Monotonic milliseconds ({@code performance.now()}). */
    long now();

    /** Runs {@code task} in a new macrotask, before any timer: the browser can paint and take input in between. */
    void postTask(Runnable task);

    /** {@code setTimeout}; returns a handle for {@link #clearTimeout}. */
    Object setTimeout(Runnable task, long delayMs);

    void clearTimeout(Object handle);
}

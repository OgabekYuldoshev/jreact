package jreact.scheduler;

/** Handle of a scheduled callback. Pass it to {@link Scheduler#cancelCallback}. */
public final class Task {
    final long id;
    final Priority priority;
    final long startTime;           // when the task becomes runnable (now + delay)
    final long expirationTime;      // startTime + the priority's timeout
    long sortIndex;                 // heap key: startTime while delayed, expirationTime once runnable
    Scheduler.Callback callback;    // null = cancelled or currently running

    Task(long id, Priority priority, Scheduler.Callback callback, long startTime, long expirationTime) {
        this.id = id; this.priority = priority; this.callback = callback;
        this.startTime = startTime; this.expirationTime = expirationTime;
    }

    public Priority priority() { return priority; }
}

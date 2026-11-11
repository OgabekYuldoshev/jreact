package jreact.scheduler;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Cooperative task scheduler (port of React's {@code scheduler} package). Tasks are ordered by expiration
 * time (= start + priority timeout), so urgent work goes first and old low-priority work cannot starve.
 * Work runs in slices of {@code frameIntervalMs}: a task that wants to continue returns a continuation, and
 * long work asks {@link #shouldYield()} to hand control back to the host between units.
 *
 * <p>Single-threaded: call everything from the thread that runs the host's callbacks.
 */
public final class Scheduler {

    /** A unit of work. {@code didTimeout} is true when the task is overdue and must not be postponed again. */
    @FunctionalInterface
    public interface Callback {
        /** Returns a continuation to keep the same task alive (same priority and deadline), or null when done. */
        Callback run(boolean didTimeout);
    }

    public static final long DEFAULT_FRAME_INTERVAL_MS = 5;

    private static final Comparator<Task> ORDER =
            Comparator.<Task>comparingLong(t -> t.sortIndex).thenComparingLong(t -> t.id);

    private final SchedulerHost host;
    private final long frameIntervalMs;
    private final PriorityQueue<Task> taskQueue = new PriorityQueue<>(ORDER);    // runnable, by expiration
    private final PriorityQueue<Task> timerQueue = new PriorityQueue<>(ORDER);   // delayed, by start time

    private long nextId = 1;
    private boolean hostCallbackScheduled;   // the queue has work and a flush has been requested
    private boolean workRequested;           // a host callback is pending
    private boolean hostTimeoutScheduled;
    private boolean performingWork;
    private Priority currentPriority = Priority.NORMAL;
    private long sliceStart;

    public Scheduler(SchedulerHost host) { this(host, DEFAULT_FRAME_INTERVAL_MS); }

    public Scheduler(SchedulerHost host, long frameIntervalMs) {
        this.host = Objects.requireNonNull(host, "host");
        this.frameIntervalMs = frameIntervalMs;
    }

    public long now() { return host.now(); }

    public Task scheduleCallback(Priority priority, Callback callback) {
        return scheduleCallback(priority, callback, 0);
    }

    public Task scheduleCallback(Priority priority, Callback callback, long delayMs) {
        long now = host.now();
        long start = delayMs > 0 ? now + delayMs : now;
        Task task = new Task(nextId++, priority, Objects.requireNonNull(callback, "callback"),
                start, start + priority.timeoutMs());

        if (start > now) {
            task.sortIndex = start;
            timerQueue.add(task);
            if (taskQueue.isEmpty() && task == timerQueue.peek()) {
                // the earliest timer changed: restart the single host timeout
                if (hostTimeoutScheduled) host.cancelHostTimeout(); else hostTimeoutScheduled = true;
                host.requestHostTimeout(this::handleTimeout, start - now);
            }
        } else {
            task.sortIndex = task.expirationTime;
            taskQueue.add(task);
            if (!hostCallbackScheduled && !performingWork) {
                hostCallbackScheduled = true;
                requestWork();
            }
        }
        return task;
    }

    /** Lazy cancel: the task stays in the heap and is skipped when it surfaces. Safe to call while it runs. */
    public void cancelCallback(Task task) {
        if (task != null) task.callback = null;
    }

    /** True once the current slice used up its time: long work should stop and return a continuation. */
    public boolean shouldYield() { return host.now() - sliceStart >= frameIntervalMs; }

    public Priority currentPriority() { return currentPriority; }

    /** Runs {@code body} with {@code priority} as the current priority (visible to {@link #currentPriority()}). */
    public void runWithPriority(Priority priority, Runnable body) {
        Priority prev = currentPriority;
        currentPriority = priority;
        try { body.run(); } finally { currentPriority = prev; }
    }

    // ---------------------------------------------------------------- host plumbing

    private void requestWork() {
        if (workRequested) return;
        workRequested = true;
        host.requestHostCallback(this::performWorkUntilDeadline);
    }

    private void performWorkUntilDeadline() {
        workRequested = false;
        sliceStart = host.now();
        boolean hasMoreWork = true;     // if a task throws, still come back for the rest
        try {
            hasMoreWork = flushWork(sliceStart);
        } finally {
            if (hasMoreWork) requestWork();
        }
    }

    private boolean flushWork(long initialTime) {
        hostCallbackScheduled = false;
        if (hostTimeoutScheduled) {
            hostTimeoutScheduled = false;
            host.cancelHostTimeout();
        }
        performingWork = true;
        Priority prev = currentPriority;
        try {
            return workLoop(initialTime);
        } finally {
            currentPriority = prev;
            performingWork = false;
        }
    }

    /** Returns true if runnable tasks remain (we yielded), false if the queue is drained. */
    private boolean workLoop(long initialTime) {
        long currentTime = initialTime;
        advanceTimers(currentTime);
        Task task = taskQueue.peek();
        while (task != null) {
            // only unexpired work may be postponed; expired work runs even past the deadline
            if (task.expirationTime > currentTime && shouldYield()) break;
            Callback callback = task.callback;
            if (callback != null) {
                task.callback = null;
                currentPriority = task.priority;
                Callback continuation = callback.run(task.expirationTime <= currentTime);
                currentTime = host.now();
                if (continuation != null) {
                    task.callback = continuation;
                } else if (task == taskQueue.peek()) {
                    taskQueue.poll();
                }
                advanceTimers(currentTime);
            } else {
                taskQueue.poll();       // cancelled
            }
            task = taskQueue.peek();
        }
        if (task != null) return true;
        Task firstTimer = timerQueue.peek();
        if (firstTimer != null) {
            hostTimeoutScheduled = true;
            host.requestHostTimeout(this::handleTimeout, firstTimer.startTime - currentTime);
        }
        return false;
    }

    /** Moves delayed tasks whose time has come into the runnable queue. */
    private void advanceTimers(long currentTime) {
        Task timer = timerQueue.peek();
        while (timer != null) {
            if (timer.callback == null) {
                timerQueue.poll();
            } else if (timer.startTime <= currentTime) {
                timerQueue.poll();
                timer.sortIndex = timer.expirationTime;
                taskQueue.add(timer);
            } else {
                return;
            }
            timer = timerQueue.peek();
        }
    }

    private void handleTimeout() {
        hostTimeoutScheduled = false;
        long now = host.now();
        advanceTimers(now);
        if (hostCallbackScheduled) return;
        if (taskQueue.peek() != null) {
            hostCallbackScheduled = true;
            requestWork();
        } else {
            Task first = timerQueue.peek();
            if (first != null) {
                hostTimeoutScheduled = true;
                host.requestHostTimeout(this::handleTimeout, first.startTime - now);
            }
        }
    }
}

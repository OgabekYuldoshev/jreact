package jreact.dom;

import jreact.scheduler.SchedulerHost;

/** The scheduler's host on top of the browser's event loop primitives. */
public final class EventLoopSchedulerHost implements SchedulerHost {

    private final Timers timers;
    private Object timeoutHandle;

    public EventLoopSchedulerHost(Timers timers) { this.timers = timers; }

    @Override public long now() { return timers.now(); }

    @Override public void requestHostCallback(Runnable work) { timers.postTask(work); }

    @Override
    public void requestHostTimeout(Runnable work, long delayMs) {
        timeoutHandle = timers.setTimeout(() -> { timeoutHandle = null; work.run(); }, delayMs);
    }

    @Override
    public void cancelHostTimeout() {
        if (timeoutHandle != null) timers.clearTimeout(timeoutHandle);
        timeoutHandle = null;
    }
}

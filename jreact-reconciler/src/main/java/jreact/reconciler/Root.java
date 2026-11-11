package jreact.reconciler;

import jreact.Element;

import jreact.scheduler.Priority;
import jreact.scheduler.Task;

import java.util.ArrayList;
import java.util.List;

/** A mounted tree: a host container plus the fiber tree rendered into it (React's FiberRoot). */
public final class Root<N> {

    private final Reconciler<N> reconciler;
    private final N container;

    Fiber current;                          // committed tree, starts as an empty ROOT fiber
    Element pendingElement;
    boolean hasPendingElement;
    boolean needsRender;                    // an update is waiting for the next render
    Priority pendingPriority;               // most urgent update since the last render started
    Fiber wipRoot;                          // render in progress (paused between slices), or null
    Fiber nextUnit;                         // where the paused render continues
    Task scheduledTask;                     // the scheduler task that will work on this root
    Priority scheduledPriority;             // priority of scheduledTask
    Task passiveTask;                       // scheduled flush of useEffects
    int nestedUpdates;                      // consecutive updates caused by effects, see Reconciler.MAX_UPDATE_PASSES
    Fiber pendingPassive;                   // committed tree whose useEffects have not run yet
    final List<Runnable> deletionCleanups = new ArrayList<>();

    Root(Reconciler<N> reconciler, N container) {
        this.reconciler = reconciler;
        this.container = container;
        this.current = new Fiber(Fiber.Tag.ROOT, null, null, null);
        this.current.stateNode = this;
    }

    public N container() { return container; }

    /** Schedules {@code element} as the new UI of this root (async, batched, at the current update priority). */
    public void render(Element element) { reconciler.updateContainer(this, element); }

    /** Renders and commits right now (never time-sliced), including effects. */
    public void renderSync(Element element) { render(element); flush(); }

    /** Runs any pending render, commit and effects right now, finishing a paused render if there is one. */
    public void flush() { reconciler.flushSync(this); }

    /** Removes everything rendered into the container and runs effect cleanups. */
    public void unmount() { renderSync(null); }
}

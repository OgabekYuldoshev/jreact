package jreact.reconciler;

import jreact.Element;
import jreact.scheduler.Priority;
import jreact.scheduler.Scheduler;
import jreact.scheduler.Task;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The Fiber reconciler. Given a {@link HostConfig} and a {@link Scheduler} it keeps a host tree in sync with
 * {@link Element} trees: state updates enqueue work on a fiber, the root re-renders top-down while skipping
 * unchanged subtrees, and the diff is committed to the host in one pass.
 *
 * <p>Updates carry a priority (see {@link #runWithPriority}). Urgent updates ({@code IMMEDIATE}..{@code NORMAL})
 * render in one go. Low priority ones ({@code LOW}, {@code IDLE}, e.g. {@link #startTransition}) render in
 * slices and yield to the host between fibers, so input stays responsive; nothing reaches the host until the
 * whole tree is ready. An urgent update that arrives while such a render is paused makes it finish at once.
 *
 * <p>Priorities apply per root, not per update (no lanes yet): one render handles every pending update.
 *
 * <pre>{@code
 * Reconciler<MyNode> r = new Reconciler<>(host, scheduler);
 * Root<MyNode> root = r.createRoot(container);
 * root.render(element);
 * }</pre>
 *
 * Not thread-safe: use one thread (the UI thread) per reconciler.
 */
public final class Reconciler<N> {

    /** React's NESTED_UPDATE_LIMIT: guards against effects that set state on every render. */
    static final int MAX_UPDATE_PASSES = 50;

    final HostConfig<N> host;
    private final Scheduler scheduler;
    private final Render<N> render = new Render<>(this);
    private final Commit<N> commit;
    final Diagnostics diagnostics = new Diagnostics();
    private Priority updatePriority = Priority.NORMAL;
    private boolean rendering;
    private boolean working;

    public Reconciler(HostConfig<N> host, Scheduler scheduler) {
        this.host = Objects.requireNonNull(host, "host");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.commit = new Commit<>(host);
    }

    public Root<N> createRoot(N container) { return new Root<>(this, container); }

    // ---------------------------------------------------------------- dev mode

    /** Dev-mode warnings (missing or duplicate keys). On by default; turn off for production builds. */
    public void setDevMode(boolean enabled) { diagnostics.setEnabled(enabled); }

    /** Where warnings go. Default: {@code System.err} (the browser console in a TeaVM build). */
    public void setWarningHandler(java.util.function.Consumer<String> handler) { diagnostics.setHandler(handler); }

    // ---------------------------------------------------------------- update priority

    /** Updates made inside {@code body} (setState, {@code root.render}) get {@code priority}. Default is NORMAL. */
    public void runWithPriority(Priority priority, Runnable body) {
        Priority prev = updatePriority;
        updatePriority = priority;
        try { body.run(); } finally { updatePriority = prev; }
    }

    /** For event handlers of direct input (click, key): USER_BLOCKING, rendered without slicing. */
    public void discreteUpdates(Runnable body) { runWithPriority(Priority.USER_BLOCKING, body); }

    /** Marks updates in {@code body} as non-urgent: LOW, rendered in slices and yielding to input. */
    public void startTransition(Runnable body) { runWithPriority(Priority.LOW, body); }

    // ---------------------------------------------------------------- scheduling

    void updateContainer(Root<N> root, Element element) {
        root.pendingElement = element;
        root.hasPendingElement = true;
        requestRender(root);
    }

    /** setState entry point (React's dispatchSetState). */
    void dispatch(UpdateQueue queue, UnaryOperator<Object> action) {
        Fiber fiber = queue.fiber;
        if (fiber.detached) return;     // component already unmounted
        if (rendering) {
            throw new IllegalStateException("State cannot be updated while rendering: do it in an event handler or useEffect");
        }
        // eager bail-out: nothing queued and the new state equals the rendered one -> no render at all
        if (queue.pending.isEmpty() && Objects.equals(action.apply(queue.lastRendered), queue.lastRendered)) return;
        queue.pending.add(action);
        Root<N> root = markUpdateFromFiberToRoot(fiber);
        if (root != null) requestRender(root);
    }

    private void requestRender(Root<N> root) {
        root.needsRender = true;
        root.pendingPriority = Priority.mostUrgent(root.pendingPriority, updatePriority);
        schedule(root, updatePriority);
    }

    /** Makes sure a scheduler task at least as urgent as {@code priority} will work on the root. */
    private void schedule(Root<N> root, Priority priority) {
        Task existing = root.scheduledTask;
        if (existing != null) {
            if (!priority.isMoreUrgentThan(root.scheduledPriority)) return;   // already covered
            scheduler.cancelCallback(existing);                                // replace by a more urgent one
        }
        root.scheduledPriority = priority;
        root.scheduledTask = scheduler.scheduleCallback(priority, didTimeout -> performConcurrentWork(root, didTimeout));
    }

    @SuppressWarnings("unchecked")
    private Root<N> markUpdateFromFiberToRoot(Fiber fiber) {
        Fiber top = Fiber.markUpdate(fiber);
        return top.tag == Fiber.Tag.ROOT ? (Root<N>) top.stateNode : null;
    }

    // ---------------------------------------------------------------- work loops

    /** The scheduler task of a root. Returns a continuation while the render is paused. */
    private Scheduler.Callback performConcurrentWork(Root<N> root, boolean didTimeout) {
        Task self = root.scheduledTask;
        if (!root.needsRender && root.wipRoot == null) {
            if (root.scheduledTask == self) root.scheduledTask = null;
            return null;                                    // a sync flush already did the work
        }
        if (working) return late -> performConcurrentWork(root, late);

        // overdue work must not be postponed again, otherwise slicing would starve it
        boolean slice = !didTimeout && root.scheduledPriority != null && root.scheduledPriority.isTimeSliced();
        boolean done;
        working = true;
        try {
            done = performRootPass(root, slice);
        } catch (RuntimeException | Error e) {
            if (root.scheduledTask == self) root.scheduledTask = null;
            throw e;
        } finally {
            working = false;
        }
        if (!done) return late -> performConcurrentWork(root, late);

        if (root.scheduledTask == self) root.scheduledTask = null;
        if (root.needsRender && root.scheduledTask == null) {
            schedule(root, root.pendingPriority != null ? root.pendingPriority : Priority.NORMAL);
        }
        return null;
    }

    /** Renders and commits everything pending right now, ignoring the time budget. */
    void flushSync(Root<N> root) {
        if (working) return;            // a pass is already running; its caller will pick the new work up
        working = true;
        try {
            while (true) {
                if (root.wipRoot != null || root.needsRender) {
                    performRootPass(root, false);
                    continue;
                }
                flushPassiveEffects(root);
                if (!root.needsRender) break;       // effects may have queued new updates
            }
        } finally {
            working = false;
        }
        if (root.scheduledTask != null) {           // nothing left for the scheduler to do
            scheduler.cancelCallback(root.scheduledTask);
            root.scheduledTask = null;
        }
    }

    /**
     * One step of work on the root: starts or resumes a render, and commits if it completes.
     * Returns false if the render was paused because {@code timeSlice} ran out of time.
     */
    private boolean performRootPass(Root<N> root, boolean timeSlice) {
        if (root.wipRoot == null) {
            flushPassiveEffects(root);                      // pending effects see the old tree, and may queue updates
            Element element = root.hasPendingElement ? root.pendingElement : root.current.memoizedElement;
            root.pendingElement = null;
            root.hasPendingElement = false;
            root.needsRender = false;
            root.pendingPriority = null;
            root.wipRoot = Fiber.createWorkInProgress(root.current, element, null);
            root.nextUnit = root.wipRoot;
        }

        rendering = true;
        try {
            while (root.nextUnit != null) {
                if (timeSlice && scheduler.shouldYield()) break;
                root.nextUnit = render.performUnitOfWork(root.nextUnit);
            }
        } catch (RuntimeException | Error e) {
            root.wipRoot = null;                            // drop the half-built tree, the committed one is intact
            root.nextUnit = null;
            throw e;
        } finally {
            rendering = false;
        }
        if (root.nextUnit != null) return false;            // paused

        Fiber finished = root.wipRoot;
        root.wipRoot = null;
        commit.commitRoot(root, finished);
        if (root.pendingPassive != null || !root.deletionCleanups.isEmpty()) {
            root.passiveTask = scheduler.scheduleCallback(Priority.NORMAL, didTimeout -> {
                root.passiveTask = null;
                flushPassiveEffects(root);
                return null;
            });
        }
        return true;
    }

    /** Runs the useEffects of the last commit (React's flushPassiveEffects). Always runs before the next render. */
    private void flushPassiveEffects(Root<N> root) {
        if (root.passiveTask != null) {
            scheduler.cancelCallback(root.passiveTask);
            root.passiveTask = null;
        }
        if (root.pendingPassive == null && root.deletionCleanups.isEmpty()) return;
        boolean hadWork = root.needsRender;
        commit.flushPassive(root);
        if (!root.needsRender) {
            root.nestedUpdates = 0;
        } else if (!hadWork && ++root.nestedUpdates > MAX_UPDATE_PASSES) {
            root.needsRender = false;
            root.nestedUpdates = 0;
            throw new IllegalStateException("Maximum update depth exceeded: a component keeps calling setState "
                    + "in useEffect (or similar) on every render");
        }
    }
}

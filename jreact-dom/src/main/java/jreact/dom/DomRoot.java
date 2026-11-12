package jreact.dom;

import jreact.Element;
import jreact.reconciler.Reconciler;
import jreact.reconciler.Root;
import jreact.scheduler.Scheduler;
import jreact.scheduler.SchedulerHost;

/**
 * Entry point: renders JReact elements into a DOM container (like React's {@code createRoot(container)}).
 * It wires {@link DomHost}, the {@link Scheduler} and the reconciler together.
 *
 * <pre>{@code
 * DomRoot<MyNode> root = DomRoot.create(ops, container, schedulerHost);
 * root.render(app);
 * }</pre>
 */
public final class DomRoot<N> {

    private final Reconciler<N> reconciler;
    private final Root<N> root;
    private final DomHost<N> host;

    private DomRoot(Reconciler<N> reconciler, Root<N> root, DomHost<N> host) {
        this.reconciler = reconciler;
        this.root = root;
        this.host = host;
    }

    public static <N> DomRoot<N> create(DomOps<N> ops, N container, SchedulerHost schedulerHost) {
        DomHost<N> host = new DomHost<>(ops, container);
        Reconciler<N> reconciler = new Reconciler<>(host, new Scheduler(schedulerHost));
        host.setUpdateRunner(reconciler::runWithPriority);
        return new DomRoot<>(reconciler, reconciler.createRoot(container), host);
    }

    /** Schedules {@code element} as the new UI (batched, async). */
    public void render(Element element) { root.render(element); }

    /** Renders and commits right now, including effects. */
    public void renderSync(Element element) { root.renderSync(element); }

    /** Removes everything this root rendered and runs effect cleanups. */
    public void unmount() { root.unmount(); }

    /** Updates made inside {@code body} are non-urgent: rendered in slices, never blocking input. */
    public void startTransition(Runnable body) { reconciler.startTransition(body); }

    /** The DOM host, e.g. to route its warnings ({@link DomHost#setWarningHandler}). */
    public DomHost<N> host() { return host; }

    public Reconciler<N> reconciler() { return reconciler; }

    public Root<N> root() { return root; }
}

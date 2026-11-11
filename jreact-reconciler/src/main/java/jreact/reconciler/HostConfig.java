package jreact.reconciler;

import jreact.Attrs;

/**
 * Everything the reconciler needs from a platform (React's "host config"). The reconciler only decides
 * <em>what</em> must change; a host (DOM, test tree, string builder...) decides <em>how</em>.
 * When to work is not the host's concern: that is the {@link jreact.scheduler.Scheduler}'s job.
 *
 * @param <N> node type of the host: used for elements, text nodes and the root container alike
 */
public interface HostConfig<N> {

    /** Creates a detached element and applies its initial attributes, event handlers are included. */
    N createInstance(String tag, Attrs initial);

    N createTextInstance(String text);

    /** Builds a not-yet-attached subtree (render phase). The parent is always a freshly created instance. */
    void appendInitialChild(N parent, N child);

    /** Commit phase. If {@code child} already has a parent it must be moved, like DOM {@code appendChild}. */
    void appendChild(N parent, N child);

    /** Commit phase. Same move semantics as {@link #appendChild}. */
    void insertBefore(N parent, N child, N before);

    void removeChild(N parent, N child);

    /** Applies only what changed. A {@code null} value in the diff means "remove". */
    void commitUpdate(N instance, AttrsDiff diff);

    void commitTextUpdate(N textInstance, String oldText, String newText);
}

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

    /** Creates a detached, empty element. Attributes come later in {@link #finalizeInitialChildren}. */
    N createInstance(String tag);

    /**
     * Applies the initial attributes and event handlers. Called once per new element, after all its children
     * were appended, so attributes that depend on children (a {@code <select>}'s value) work.
     */
    void finalizeInitialChildren(N instance, Attrs initial);

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

    // ---------------------------------------------------------------- hydration (optional)

    /**
     * Hydration: the nodes already inside {@code parent} that the server rendered (elements and text; comments
     * are left out), or null when this host cannot hydrate (then {@link Root#hydrate} renders normally).
     */
    default java.util.List<N> hydratableChildren(N parent) { return null; }

    /** Hydration: is this existing node a text node? (otherwise it is an element) */
    default boolean isTextNode(N node) { return false; }

    /** Hydration: lower-case tag name of an existing element. */
    default String tagName(N node) { return null; }

    /**
     * Hydration: {@code existing} (server HTML) replaces {@code created} (just built by the render phase) as the node
     * of an element. Carry over whatever {@link #finalizeInitialChildren} attached to {@code created} (event handlers).
     */
    default void hydrateInstance(N existing, N created) {}

    /** Hydration: an existing text node stands for {@code text}; fix its content when the server sent something else. */
    default void hydrateText(N existing, String text) {}

    /** Hydration: the server HTML did not match what the client rendered; the client version was put in place. */
    default void hydrationMismatch(String message) {}
}

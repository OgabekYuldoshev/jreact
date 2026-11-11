package jreact.reconciler;

import jreact.Component;
import jreact.Context;
import jreact.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A unit of work and a node of the fiber tree (React's FiberNode). The tree is double-buffered:
 * every fiber has at most one {@link #alternate}. One of the pair is the committed ("current") one,
 * the other is the work-in-progress one that the next render mutates.
 */
final class Fiber {

    enum Tag { ROOT, HOST, TEXT, COMPONENT, FRAGMENT, PROVIDER }

    // flags: work this fiber needs in the commit phase
    static final int PLACEMENT = 1;       // insert or move host nodes
    static final int UPDATE = 2;          // apply attrs/text diff
    static final int CHILD_DELETION = 4;  // `deletions` is non-empty
    static final int REF = 8;             // attach/detach ref
    static final int PASSIVE = 16;        // has useEffect work
    static final int MUTATION_MASK = PLACEMENT | UPDATE | CHILD_DELETION;

    final Tag tag;
    final String key;
    final Object type;                                  // HOST: String tag, COMPONENT: Class<?>, PROVIDER: Context<?>
    final Supplier<? extends Component<?>> factory;     // COMPONENT

    int index;                                          // position among siblings
    Fiber ret, child, sibling, alternate;

    Element element;            // input of the current render (null for TEXT, may be null for ROOT)
    Element memoizedElement;    // input of the last completed render
    String text;
    String memoizedText;

    Object stateNode;           // HOST/TEXT: host node, ROOT: the Root
    Component<?> instance;      // COMPONENT
    Hook hooks;                 // COMPONENT: head of the hook list, committed state
    List<HookEffect> effects;   // COMPONENT: effects to run after this commit
    List<Context<?>> contexts;  // COMPONENT: contexts read by the last render (React's dependencies)
    AttrsDiff updatePayload;    // HOST

    int flags, subtreeFlags;
    List<Fiber> deletions;      // current children to remove in this commit
    boolean hasUpdate;          // a setState is waiting on this fiber (React: lanes)
    boolean childHasUpdate;     // some descendant has hasUpdate (React: childLanes)
    boolean detached;           // unmounted: late setState calls are ignored

    Fiber(Tag tag, String key, Object type, Supplier<? extends Component<?>> factory) {
        this.tag = tag; this.key = key; this.type = type; this.factory = factory;
    }

    List<HookEffect> effects() {
        if (effects == null) effects = new ArrayList<>(2);
        return effects;
    }

    /** React's createWorkInProgress: reuse (or lazily create) the alternate and reset its per-render state. */
    static Fiber createWorkInProgress(Fiber current, Element element, String text) {
        Fiber w = current.alternate;
        if (w == null) {
            w = new Fiber(current.tag, current.key, current.type, current.factory);
            w.alternate = current;
            current.alternate = w;
        }
        w.stateNode = current.stateNode;
        w.element = element;
        w.text = text;
        w.flags = 0;
        w.subtreeFlags = 0;
        w.deletions = null;
        w.effects = null;
        w.updatePayload = null;
        w.child = current.child;
        w.memoizedElement = current.memoizedElement;
        w.memoizedText = current.memoizedText;
        w.hooks = current.hooks;
        w.contexts = current.contexts;
        w.instance = current.instance;
        w.index = current.index;
        w.hasUpdate = current.hasUpdate;
        w.childHasUpdate = current.childHasUpdate;
        return w;
    }

    /**
     * Flags the fiber and every ancestor, on both alternates, so the next render can find its way down.
     * Returns the top-most fiber reached (the ROOT fiber for a mounted tree).
     */
    static Fiber markUpdate(Fiber fiber) {
        fiber.hasUpdate = true;
        if (fiber.alternate != null) fiber.alternate.hasUpdate = true;
        Fiber node = fiber;
        for (Fiber parent = node.ret; parent != null; parent = node.ret) {
            parent.childHasUpdate = true;
            if (parent.alternate != null) parent.alternate.childHasUpdate = true;
            node = parent;
        }
        return node;
    }

    @Override public String toString() { return tag + (type == null ? "" : "(" + type + ")") + (key == null ? "" : "#" + key); }
}

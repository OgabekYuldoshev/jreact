package jreact.reconciler;

import jreact.Attrs;
import jreact.Element;
import jreact.Hooks;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Render phase (React's ReactFiberBeginWork + CompleteWork + WorkLoop). Pure with respect to the host tree:
 * it only builds the work-in-progress fiber tree and creates detached host nodes.
 */
final class Render<N> {

    private final Reconciler<N> reconciler;

    Render(Reconciler<N> reconciler) { this.reconciler = reconciler; }

    /**
     * Processes one fiber and returns the next one to process, or null when the whole tree is done.
     * The caller drives the loop, so it can stop between any two units and resume later.
     */
    Fiber performUnitOfWork(Fiber unit) {
        Fiber next = beginWork(unit.alternate, unit);
        unit.memoizedElement = unit.element;
        unit.memoizedText = unit.text;
        return next != null ? next : completeUnitOfWork(unit);
    }

    // ---------------------------------------------------------------- begin: go down

    private Fiber beginWork(Fiber current, Fiber wip) {
        if (current != null) {
            boolean sameInput = wip.tag == Fiber.Tag.TEXT
                    ? Objects.equals(wip.text, current.memoizedText)
                    : wip.element == current.memoizedElement;
            if (sameInput && !wip.hasUpdate) return bailout(wip);
        }
        wip.hasUpdate = false;
        switch (wip.tag) {
            case ROOT -> reconcileChildren(current, wip, Arrays.asList(wip.element));
            case HOST, FRAGMENT -> reconcileChildren(current, wip, wip.element.children());
            case COMPONENT -> updateComponent(current, wip);
            case TEXT -> { return null; }
        }
        return wip.child;
    }

    private void updateComponent(Fiber current, Fiber wip) {
        if (wip.instance == null) wip.instance = wip.factory.get();
        FiberHooks hooks = new FiberHooks(reconciler, wip, current);
        Hooks.Dispatcher prev = Hooks.swap(hooks);
        Element out;
        try {
            out = wip.instance.renderWith(wip.element.props());
            hooks.finish();
        } finally {
            Hooks.swap(prev);
        }
        reconcileChildren(current, wip, Arrays.asList(out));
    }

    private void reconcileChildren(Fiber current, Fiber wip, List<Object> children) {
        wip.child = current == null
                ? ChildReconciler.MOUNT.reconcile(wip, null, children)
                : ChildReconciler.UPDATE.reconcile(wip, current.child, children);
    }

    /** Same input and no setState here: skip this fiber, and the whole subtree unless a descendant has work. */
    private Fiber bailout(Fiber wip) {
        if (!wip.childHasUpdate) return null;
        Fiber cur = wip.child;
        if (cur == null) return null;
        Fiber nw = Fiber.createWorkInProgress(cur, cur.memoizedElement, cur.memoizedText);
        wip.child = nw;
        nw.ret = wip;
        while (cur.sibling != null) {
            cur = cur.sibling;
            nw = nw.sibling = Fiber.createWorkInProgress(cur, cur.memoizedElement, cur.memoizedText);
            nw.ret = wip;
        }
        nw.sibling = null;
        return wip.child;
    }

    // ---------------------------------------------------------------- complete: go up

    private Fiber completeUnitOfWork(Fiber unit) {
        Fiber node = unit;
        while (node != null) {
            Fiber parent = node.ret;
            completeWork(node.alternate, node);
            if (node.sibling != null) return node.sibling;
            node = parent;
        }
        return null;
    }

    private void completeWork(Fiber current, Fiber wip) {
        HostConfig<N> host = reconciler.host;
        switch (wip.tag) {
            case HOST -> {
                Attrs next = wip.element.attrs();
                if (current != null && wip.stateNode != null) {
                    if (wip.element != current.memoizedElement) {
                        Attrs prev = current.memoizedElement.attrs();
                        AttrsDiff diff = AttrsDiff.between(prev, next);
                        if (!diff.isEmpty()) { wip.updatePayload = diff; wip.flags |= Fiber.UPDATE; }
                        if (prev.ref() != next.ref()) wip.flags |= Fiber.REF;
                    }
                } else {
                    N node = host.createInstance((String) wip.type, next);
                    appendAllChildren(node, wip);
                    wip.stateNode = node;
                    if (next.ref() != null) wip.flags |= Fiber.REF;
                }
            }
            case TEXT -> {
                if (current != null && wip.stateNode != null) {
                    if (!wip.text.equals(current.memoizedText)) wip.flags |= Fiber.UPDATE;
                } else {
                    wip.stateNode = host.createTextInstance(wip.text);
                }
            }
            default -> {}
        }
        bubbleProperties(current, wip);
    }

    /** Appends the nearest host nodes below {@code wip} to a freshly created host instance. */
    @SuppressWarnings("unchecked")
    private void appendAllChildren(N parent, Fiber wip) {
        Fiber node = wip.child;
        while (node != null) {
            if (node.tag == Fiber.Tag.HOST || node.tag == Fiber.Tag.TEXT) {
                reconciler.host.appendInitialChild(parent, (N) node.stateNode);
            } else if (node.child != null) {
                node.child.ret = node;
                node = node.child;
                continue;
            }
            if (node == wip) return;
            while (node.sibling == null) {
                if (node.ret == null || node.ret == wip) return;
                node = node.ret;
            }
            node.sibling.ret = node.ret;
            node = node.sibling;
        }
    }

    /** Collects children's flags so the commit phase can skip untouched subtrees. */
    private void bubbleProperties(Fiber current, Fiber wip) {
        boolean didBailout = current != null && current.child == wip.child;
        int sub = 0;
        boolean childUpdate = false;
        if (!didBailout) {
            for (Fiber c = wip.child; c != null; c = c.sibling) {
                sub |= c.subtreeFlags | c.flags;
                childUpdate |= c.hasUpdate || c.childHasUpdate;
            }
        }
        wip.subtreeFlags = sub;
        wip.childHasUpdate = childUpdate;
    }
}

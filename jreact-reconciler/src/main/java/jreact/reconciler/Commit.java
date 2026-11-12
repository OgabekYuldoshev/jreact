package jreact.reconciler;

import jreact.Ref;

import java.util.ArrayList;
import java.util.List;

/**
 * Commit phase (React's ReactFiberCommitWork): applies the finished work to the host. Runs synchronously and
 * never throws on purpose-built state, so the host tree is never left half-updated by render errors.
 * Order: mutation (deletions, placements, updates) -> swap current -> layout (refs) -> passive (useEffect).
 */
final class Commit<N> {

    private final Reconciler<N> reconciler;
    private final HostConfig<N> host;

    Commit(Reconciler<N> reconciler) { this.reconciler = reconciler; this.host = reconciler.host; }

    void commitRoot(Root<N> root, Fiber finished) {
        if (root.hydrating) {
            root.hydrating = false;
            if (root.current.child == null) hydrate(root, finished);
        }
        mutate(root, finished);
        root.current = finished;
        layout(finished);
        root.pendingPassive = ((finished.flags | finished.subtreeFlags) & Fiber.PASSIVE) != 0 ? finished : null;
    }

    // ---------------------------------------------------------------- hydration

    /** Adopts the container's existing nodes for the freshly rendered tree (first commit only), see {@link Root#hydrate}. */
    private void hydrate(Root<N> root, Fiber finished) {
        List<N> existing = host.hydratableChildren(root.container());
        if (existing == null || existing.isEmpty()) return;      // nothing from the server: a normal first render
        List<Fiber> fibers = new ArrayList<>();
        hostFibers(finished, fibers, true);
        hydrateChildren(root.container(), fibers, existing);
    }

    /** The nearest host fibers below {@code parent}; {@code clear} drops PLACEMENT (the nodes are already in the page). */
    private void hostFibers(Fiber parent, List<Fiber> out, boolean clear) {
        for (Fiber c = parent.child; c != null; c = c.sibling) {
            if (clear) c.flags &= ~Fiber.PLACEMENT;
            if (c.tag == Fiber.Tag.HOST || c.tag == Fiber.Tag.TEXT) out.add(c);
            else hostFibers(c, out, clear);
        }
    }

    @SuppressWarnings("unchecked")
    private void hydrateChildren(N parent, List<Fiber> fibers, List<N> existing) {
        int i = 0;
        for (Fiber f : fibers) {
            N created = (N) f.stateNode;
            boolean text = f.tag == Fiber.Tag.TEXT;
            N cur = i < existing.size() ? existing.get(i) : null;
            if (text && f.text.isEmpty()) {              // HTML cannot carry an empty text node: keep the client's
                place(parent, created, cur);
                continue;
            }
            if (cur != null && (text ? host.isTextNode(cur)
                    : !host.isTextNode(cur) && ((String) f.type).equalsIgnoreCase(host.tagName(cur)))) {
                i++;
                f.stateNode = cur;
                if (text) {
                    host.hydrateText(cur, f.text);
                } else {
                    host.hydrateInstance(cur, created);
                    List<N> inner = host.hydratableChildren(cur);
                    List<Fiber> innerFibers = new ArrayList<>();
                    hostFibers(f, innerFibers, false);
                    hydrateChildren(cur, innerFibers, inner == null ? new ArrayList<>() : inner);
                }
            } else {
                host.hydrationMismatch("expected " + (text ? "text \"" + f.text + "\"" : "<" + f.type + ">")
                        + " but the server HTML has " + describe(cur));
                place(parent, created, cur);
                if (cur != null) { host.removeChild(parent, cur); i++; }
            }
        }
        for (; i < existing.size(); i++) {
            host.hydrationMismatch("the server HTML has an extra " + describe(existing.get(i)));
            host.removeChild(parent, existing.get(i));
        }
    }

    private void place(N parent, N node, N before) {
        if (before != null) host.insertBefore(parent, node, before); else host.appendChild(parent, node);
    }

    private String describe(N node) {
        if (node == null) return "nothing";
        return host.isTextNode(node) ? "a text node" : "<" + host.tagName(node) + ">";
    }

    // ---------------------------------------------------------------- mutation

    private void mutate(Root<N> root, Fiber fiber) {
        if ((fiber.flags & Fiber.CHILD_DELETION) != 0 && fiber.deletions != null) {
            for (Fiber d : fiber.deletions) commitDeletion(root, d, fiber);
        }
        if ((fiber.subtreeFlags & Fiber.MUTATION_MASK) != 0) {
            for (Fiber c = fiber.child; c != null; c = c.sibling) mutate(root, c);
        }
        if ((fiber.flags & Fiber.PLACEMENT) != 0) {
            commitPlacement(root, fiber);
            fiber.flags &= ~Fiber.PLACEMENT;     // a later sibling search must see it as already placed
        }
        if ((fiber.flags & Fiber.UPDATE) != 0) commitUpdate(fiber);
    }

    @SuppressWarnings("unchecked")
    private void commitUpdate(Fiber fiber) {
        if (fiber.tag == Fiber.Tag.HOST) {
            if (fiber.updatePayload != null) host.commitUpdate((N) fiber.stateNode, fiber.updatePayload);
        } else if (fiber.tag == Fiber.Tag.TEXT) {
            host.commitTextUpdate((N) fiber.stateNode, fiber.alternate.memoizedText, fiber.memoizedText);
        }
    }

    // ---- placement

    private void commitPlacement(Root<N> root, Fiber fiber) {
        N parent = hostParentNode(root, fiber.ret);
        insertOrAppend(fiber, hostSibling(fiber), parent);
    }

    @SuppressWarnings("unchecked")
    private N hostParentNode(Root<N> root, Fiber from) {
        Fiber p = from;
        while (p.tag != Fiber.Tag.HOST && p.tag != Fiber.Tag.ROOT) p = p.ret;
        return p.tag == Fiber.Tag.ROOT ? root.container() : (N) p.stateNode;
    }

    /** First already-placed host node after {@code fiber} inside the same host parent, or null (= append). */
    @SuppressWarnings("unchecked")
    private N hostSibling(Fiber fiber) {
        Fiber node = fiber;
        search:
        while (true) {
            while (node.sibling == null) {
                if (node.ret == null || isHostParent(node.ret)) return null;
                node = node.ret;
            }
            node.sibling.ret = node.ret;
            node = node.sibling;
            while (node.tag != Fiber.Tag.HOST && node.tag != Fiber.Tag.TEXT) {
                if ((node.flags & Fiber.PLACEMENT) != 0) continue search;   // itself being inserted
                if (node.child == null) continue search;
                node.child.ret = node;
                node = node.child;
            }
            if ((node.flags & Fiber.PLACEMENT) == 0) return (N) node.stateNode;
        }
    }

    private static boolean isHostParent(Fiber f) { return f.tag == Fiber.Tag.HOST || f.tag == Fiber.Tag.ROOT; }

    @SuppressWarnings("unchecked")
    private void insertOrAppend(Fiber fiber, N before, N parent) {
        if (fiber.tag == Fiber.Tag.HOST || fiber.tag == Fiber.Tag.TEXT) {
            N node = (N) fiber.stateNode;
            if (before != null) host.insertBefore(parent, node, before); else host.appendChild(parent, node);
        } else {
            for (Fiber c = fiber.child; c != null; c = c.sibling) insertOrAppend(c, before, parent);
        }
    }

    // ---- deletion

    private void commitDeletion(Root<N> root, Fiber deleted, Fiber returnFiber) {
        removeHostNodes(deleted, hostParentNode(root, returnFiber));
        unmount(root, deleted);
    }

    @SuppressWarnings("unchecked")
    private void removeHostNodes(Fiber fiber, N parent) {
        if (fiber.tag == Fiber.Tag.HOST || fiber.tag == Fiber.Tag.TEXT) {
            host.removeChild(parent, (N) fiber.stateNode);
            return;
        }
        for (Fiber c = fiber.child; c != null; c = c.sibling) removeHostNodes(c, parent);
    }

    /** Marks the subtree dead, detaches refs and queues effect cleanups (they run in the passive phase). */
    private void unmount(Root<N> root, Fiber fiber) {
        fiber.detached = true;
        if (fiber.alternate != null) fiber.alternate.detached = true;
        if (fiber.tag == Fiber.Tag.COMPONENT) {
            for (Hook h = fiber.hooks; h != null; h = h.next) {
                if (h.kind == Hook.Kind.EFFECT) {
                    HookEffect e = (HookEffect) h.memoizedState;
                    if (e.destroy != null) { root.deletionCleanups.add(e.destroy); e.destroy = null; }
                }
            }
        } else if (fiber.tag == Fiber.Tag.HOST && fiber.memoizedElement != null) {
            detachRef(fiber.memoizedElement.attrs().ref());
        }
        for (Fiber c = fiber.child; c != null; c = c.sibling) unmount(root, c);
    }

    // ---------------------------------------------------------------- layout: refs

    private void layout(Fiber fiber) {
        if ((fiber.subtreeFlags & Fiber.LAYOUT_MASK) != 0) {
            for (Fiber c = fiber.child; c != null; c = c.sibling) layout(c);
        }
        if ((fiber.flags & Fiber.REF) != 0) attachRef(fiber);
        if ((fiber.flags & Fiber.CAUGHT) != 0) reconciler.notifyCaught(fiber);
    }

    @SuppressWarnings("unchecked")
    private void attachRef(Fiber fiber) {
        Ref<?> next = fiber.memoizedElement.attrs().ref();
        Fiber old = fiber.alternate;
        if (old != null && old.memoizedElement != null) {
            Ref<?> prev = old.memoizedElement.attrs().ref();
            if (prev != null && prev != next) detachRef(prev);
        }
        if (next != null) ((Ref<Object>) next).current = fiber.stateNode;
    }

    private static void detachRef(Ref<?> ref) {
        if (ref != null) ref.current = null;
    }

    // ---------------------------------------------------------------- passive: useEffect

    /** All cleanups first (deleted components, then changed effects), then all new effects. Children before parents. */
    void flushPassive(Root<N> root) {
        Fiber finished = root.pendingPassive;
        root.pendingPassive = null;
        if (!root.deletionCleanups.isEmpty()) {
            List<Runnable> cleanups = new ArrayList<>(root.deletionCleanups);
            root.deletionCleanups.clear();
            for (Runnable r : cleanups) r.run();
        }
        if (finished == null) return;
        passiveUnmount(finished);
        passiveMount(finished);
    }

    private void passiveUnmount(Fiber fiber) {
        if ((fiber.subtreeFlags & Fiber.PASSIVE) != 0) {
            for (Fiber c = fiber.child; c != null; c = c.sibling) passiveUnmount(c);
        }
        if ((fiber.flags & Fiber.PASSIVE) != 0 && fiber.effects != null) {
            for (HookEffect e : fiber.effects) {
                if (e.prev != null && e.prev.destroy != null) {
                    Runnable destroy = e.prev.destroy;
                    e.prev.destroy = null;
                    try { destroy.run(); } catch (RuntimeException | Error ex) { effectFailed(fiber, ex); }
                }
            }
        }
    }

    private void passiveMount(Fiber fiber) {
        if ((fiber.subtreeFlags & Fiber.PASSIVE) != 0) {
            for (Fiber c = fiber.child; c != null; c = c.sibling) passiveMount(c);
        }
        if ((fiber.flags & Fiber.PASSIVE) != 0 && fiber.effects != null) {
            for (HookEffect e : fiber.effects) {
                e.prev = null;
                try { e.destroy = e.create.run(); } catch (RuntimeException | Error ex) { effectFailed(fiber, ex); }
            }
        }
    }

    /** An effect threw: the nearest error boundary above shows its fallback, otherwise the error propagates. */
    private void effectFailed(Fiber fiber, Throwable error) {
        if (reconciler.captureEffectError(fiber, error)) return;
        if (error instanceof RuntimeException re) throw re;
        throw (Error) error;
    }
}

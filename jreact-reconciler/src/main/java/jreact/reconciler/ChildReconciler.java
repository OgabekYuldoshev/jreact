package jreact.reconciler;

import jreact.Element;
import jreact.Jsx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Child diffing (React's ChildReconciler). {@link #UPDATE} tracks side effects (placements, deletions),
 * {@link #MOUNT} is used under fibers that have no committed counterpart yet, where everything is new anyway.
 */
final class ChildReconciler {

    static final ChildReconciler MOUNT = new ChildReconciler(false);
    static final ChildReconciler UPDATE = new ChildReconciler(true);

    private final boolean track;

    private ChildReconciler(boolean track) { this.track = track; }

    /** Returns the first work-in-progress child of {@code returnFiber}; siblings are linked through it. */
    Fiber reconcile(Fiber returnFiber, Fiber currentFirstChild, List<Object> newChildren) {
        final int n = newChildren.size();
        Fiber resultingFirst = null, previousNew = null;
        Fiber oldFiber = currentFirstChild, nextOldFiber = null;
        int lastPlacedIndex = 0, newIdx = 0;

        // 1. walk both lists while keys line up
        for (; oldFiber != null && newIdx < n; newIdx++) {
            if (oldFiber.index > newIdx) { nextOldFiber = oldFiber; oldFiber = null; }
            else nextOldFiber = oldFiber.sibling;

            Fiber newFiber = updateSlot(returnFiber, oldFiber, newChildren.get(newIdx));
            if (newFiber == null) {
                if (oldFiber == null) oldFiber = nextOldFiber;
                break;
            }
            if (track && oldFiber != null && newFiber.alternate == null) deleteChild(returnFiber, oldFiber);
            lastPlacedIndex = placeChild(newFiber, lastPlacedIndex, newIdx);
            if (previousNew == null) resultingFirst = newFiber; else previousNew.sibling = newFiber;
            previousNew = newFiber;
            oldFiber = nextOldFiber;
        }

        // 2. new list exhausted: the rest of the old list is gone
        if (newIdx == n) {
            deleteRemainingChildren(returnFiber, oldFiber);
            return resultingFirst;
        }

        // 3. old list exhausted: the rest is new
        if (oldFiber == null) {
            for (; newIdx < n; newIdx++) {
                Fiber f = createChild(returnFiber, newChildren.get(newIdx));
                if (f == null) continue;
                lastPlacedIndex = placeChild(f, lastPlacedIndex, newIdx);
                if (previousNew == null) resultingFirst = f; else previousNew.sibling = f;
                previousNew = f;
            }
            return resultingFirst;
        }

        // 4. keys diverged: look the remaining old fibers up by key (or index)
        Map<Object, Fiber> existing = new HashMap<>();
        for (Fiber f = oldFiber; f != null; f = f.sibling) existing.put(f.key != null ? f.key : (Object) f.index, f);

        for (; newIdx < n; newIdx++) {
            Fiber f = updateFromMap(existing, returnFiber, newIdx, newChildren.get(newIdx));
            if (f == null) continue;
            if (track && f.alternate != null) existing.remove(f.key != null ? f.key : (Object) newIdx);
            lastPlacedIndex = placeChild(f, lastPlacedIndex, newIdx);
            if (previousNew == null) resultingFirst = f; else previousNew.sibling = f;
            previousNew = f;
        }
        if (track) for (Fiber f : existing.values()) deleteChild(returnFiber, f);
        return resultingFirst;
    }

    // ---- slot matching

    private Fiber updateSlot(Fiber returnFiber, Fiber oldFiber, Object raw) {
        Object child = normalize(raw);
        if (child == null) return null;
        String oldKey = oldFiber == null ? null : oldFiber.key;
        if (!Objects.equals(keyOf(child), oldKey)) return null;
        if (oldFiber != null && matches(oldFiber, child)) return useFiber(oldFiber, child, returnFiber);
        return createFiber(child, returnFiber);
    }

    private Fiber updateFromMap(Map<Object, Fiber> existing, Fiber returnFiber, int newIdx, Object raw) {
        Object child = normalize(raw);
        if (child == null) return null;
        String key = keyOf(child);
        Fiber matched = existing.get(key != null ? key : (Object) newIdx);
        if (matched != null && matches(matched, child)) return useFiber(matched, child, returnFiber);
        return createFiber(child, returnFiber);
    }

    private Fiber createChild(Fiber returnFiber, Object raw) {
        Object child = normalize(raw);
        return child == null ? null : createFiber(child, returnFiber);
    }

    // ---- fiber creation

    private static Fiber useFiber(Fiber old, Object child, Fiber returnFiber) {
        Fiber w = child instanceof Element e
                ? Fiber.createWorkInProgress(old, e, null)
                : Fiber.createWorkInProgress(old, null, (String) child);
        w.sibling = null;
        w.ret = returnFiber;
        return w;
    }

    private static Fiber createFiber(Object child, Fiber returnFiber) {
        Fiber f;
        if (child instanceof Element e) {
            f = switch (e.kind()) {
                case HOST -> new Fiber(Fiber.Tag.HOST, keyOf(e), e.tag(), null);
                case COMPONENT -> new Fiber(Fiber.Tag.COMPONENT, keyOf(e), e.type(), e.factory());
                case FRAGMENT -> new Fiber(Fiber.Tag.FRAGMENT, keyOf(e), null, null);
                case PROVIDER -> new Fiber(Fiber.Tag.PROVIDER, keyOf(e), e.context(), null);
            };
            f.element = e;
        } else {
            f = new Fiber(Fiber.Tag.TEXT, null, null, null);
            f.text = (String) child;
        }
        f.ret = returnFiber;
        return f;
    }

    private static boolean matches(Fiber f, Object child) {
        if (child instanceof Element e) {
            return switch (e.kind()) {
                case HOST -> f.tag == Fiber.Tag.HOST && f.type.equals(e.tag());
                case COMPONENT -> f.tag == Fiber.Tag.COMPONENT && f.type == e.type();
                case FRAGMENT -> f.tag == Fiber.Tag.FRAGMENT;
                case PROVIDER -> f.tag == Fiber.Tag.PROVIDER && f.type == e.context();
            };
        }
        return f.tag == Fiber.Tag.TEXT;
    }

    /** null/Boolean/"" -> nothing, List -> keyless fragment, scalars -> text, Element stays. */
    private static Object normalize(Object child) {
        if (child == null || child instanceof Boolean) return null;
        if (child instanceof Element) return child;
        if (child instanceof List<?> list) return Jsx.fragment(null, list.toArray());
        String s = String.valueOf(child);
        return s.isEmpty() ? null : s;
    }

    private static String keyOf(Object child) {
        return child instanceof Element e && e.key() != null ? String.valueOf(e.key()) : null;
    }

    // ---- side effects

    private int placeChild(Fiber f, int lastPlacedIndex, int newIndex) {
        f.index = newIndex;
        if (!track) return lastPlacedIndex;
        Fiber current = f.alternate;
        if (current == null) {
            f.flags |= Fiber.PLACEMENT;      // brand new
            return lastPlacedIndex;
        }
        int oldIndex = current.index;
        if (oldIndex < lastPlacedIndex) {
            f.flags |= Fiber.PLACEMENT;      // moved forward
            return lastPlacedIndex;
        }
        return oldIndex;                     // stays put
    }

    private void deleteChild(Fiber returnFiber, Fiber child) {
        if (!track) return;
        if (returnFiber.deletions == null) returnFiber.deletions = new java.util.ArrayList<>();
        returnFiber.deletions.add(child);
        returnFiber.flags |= Fiber.CHILD_DELETION;
    }

    private void deleteRemainingChildren(Fiber returnFiber, Fiber from) {
        if (!track) return;
        for (Fiber f = from; f != null; f = f.sibling) deleteChild(returnFiber, f);
    }
}

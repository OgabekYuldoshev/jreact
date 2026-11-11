package jreact.reconciler;

import jreact.Effect;
import jreact.Hooks;
import jreact.Ref;
import jreact.State;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Hook dispatcher of one component render (React's renderWithHooks + mount/update dispatchers).
 * On mount it builds a fresh hook list, on update it clones the committed list slot by slot.
 */
final class FiberHooks implements Hooks.Dispatcher {

    private final Reconciler<?> reconciler;
    private final Fiber wip;
    private final Fiber current;        // null on mount
    private Hook currentCursor;         // last committed hook consumed
    private Hook first, last;

    FiberHooks(Reconciler<?> reconciler, Fiber wip, Fiber current) {
        this.reconciler = reconciler; this.wip = wip; this.current = current;
    }

    private boolean mount() { return current == null; }

    private Hook next(Hook.Kind kind) {
        Hook h = new Hook(kind);
        if (!mount()) {
            Hook c = currentCursor == null ? current.hooks : currentCursor.next;
            if (c == null) {
                throw new IllegalStateException("Rendered more hooks than during the previous render: hooks must not be called conditionally");
            }
            if (c.kind != kind) {
                throw new IllegalStateException("Hooks were called in a different order: expected " + c.kind + " but got " + kind);
            }
            currentCursor = c;
            h.memoizedState = c.memoizedState;
            h.queue = c.queue;
        }
        if (first == null) first = h; else last.next = h;
        last = h;
        return h;
    }

    /** Called after render() returned: validates the hook count and publishes the list on the fiber. */
    void finish() {
        if (!mount()) {
            Hook rest = currentCursor == null ? current.hooks : currentCursor.next;
            if (rest != null) {
                throw new IllegalStateException("Rendered fewer hooks than during the previous render: hooks must not be called conditionally");
            }
        }
        wip.hooks = first;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> State<T> useState(T initial) {
        Hook h = next(Hook.Kind.STATE);
        UpdateQueue q;
        T value;
        if (mount()) {
            q = new UpdateQueue(reconciler, wip, initial);
            h.queue = q;
            value = initial;
        } else {
            q = h.queue;
            value = (T) h.memoizedState;
            for (UnaryOperator<Object> op : q.pending) value = (T) op.apply(value);
            q.pending.clear();
            q.lastRendered = value;
        }
        h.memoizedState = value;
        return new StateImpl<>(value, q);
    }

    @Override
    public void useEffect(Effect effect, Object[] deps) {
        Hook h = next(Hook.Kind.EFFECT);
        if (mount()) {
            h.memoizedState = schedule(new HookEffect(effect, deps), null);
            return;
        }
        HookEffect prev = (HookEffect) h.memoizedState;
        // no deps = every render, same as the documented API
        if (deps != null && deps.length > 0 && depsEqual(prev.deps, deps)) return;
        h.memoizedState = schedule(new HookEffect(effect, deps), prev);
    }

    private HookEffect schedule(HookEffect e, HookEffect prev) {
        e.prev = prev;
        wip.flags |= Fiber.PASSIVE;
        wip.effects().add(e);
        return e;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T useMemo(Supplier<T> compute, Object[] deps) {
        Hook h = next(Hook.Kind.MEMO);
        if (!mount()) {
            MemoState prev = (MemoState) h.memoizedState;
            if (deps != null && deps.length > 0 && depsEqual(prev.deps(), deps)) return (T) prev.value();
        }
        T value = compute.get();
        h.memoizedState = new MemoState(value, deps);
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Ref<T> useRef(T initial) {
        Hook h = next(Hook.Kind.REF);
        if (mount()) h.memoizedState = new Ref<>(initial);
        return (Ref<T>) h.memoizedState;
    }

    private static boolean depsEqual(Object[] a, Object[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!Objects.equals(a[i], b[i])) return false;
        return true;
    }

    /** The {@link State} a component sees during one render: {@code get()} is a snapshot, like a React closure. */
    private static final class StateImpl<T> implements State<T> {
        private final T value;
        private final UpdateQueue queue;

        StateImpl(T value, UpdateQueue queue) { this.value = value; this.queue = queue; }

        @Override public T get() { return value; }

        @Override public void set(T v) { queue.reconciler.dispatch(queue, s -> v); }

        @Override
        @SuppressWarnings("unchecked")
        public void update(UnaryOperator<T> fn) { queue.reconciler.dispatch(queue, s -> fn.apply((T) s)); }
    }
}

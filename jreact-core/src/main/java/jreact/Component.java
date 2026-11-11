package jreact;

import java.util.function.Supplier;

/**
 * Base class of every component. Like a React function component, {@link #render} is called
 * on every update and hooks are matched by call order, so they must not be called conditionally.
 * The instance itself belongs to one fiber and lives as long as that fiber is mounted.
 */
public abstract class Component<P> {

    public abstract Element render(P props);

    /** Untyped entry point used by renderers/reconcilers, which only hold props as {@code Object}. */
    @SuppressWarnings("unchecked")
    public final Element renderWith(Object props) { return render((P) props); }

    protected final <T> State<T> useState(T initial) { return Hooks.current().useState(initial); }

    /** Effect without cleanup: {@code useEffect(() -> log("x"), dep1, dep2)}. No deps = every render. */
    protected final void useEffect(Runnable effect, Object... deps) {
        Hooks.current().useEffect(() -> { effect.run(); return null; }, deps);
    }

    /** Effect with cleanup: {@code useEffect(() -> { sub(); return () -> unsub(); }, id)}. */
    protected final void useEffect(Effect effect, Object... deps) { Hooks.current().useEffect(effect, deps); }

    protected final <T> T useMemo(Supplier<T> compute, Object... deps) { return Hooks.current().useMemo(compute, deps); }

    protected final <T> Ref<T> useRef(T initial) { return Hooks.current().useRef(initial); }
}

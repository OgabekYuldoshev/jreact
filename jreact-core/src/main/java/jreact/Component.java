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

    /**
     * Deps, like React's dependency array: no argument = runs after every render; {@link #ONCE} (an empty
     * array, React's {@code []}) = only after the first render; values = when one of them is not
     * {@code equals} to the previous render's.
     */
    protected static final Object[] ONCE = new Object[0];

    /** Effect without cleanup, after every render: {@code useEffect(() -> log("x"))}. */
    protected final void useEffect(Runnable effect) { useEffect(effect, (Object[]) null); }

    /** Effect with cleanup, after every render. */
    protected final void useEffect(Effect effect) { useEffect(effect, (Object[]) null); }

    /** Effect without cleanup: {@code useEffect(() -> log("x"), dep1, dep2)} or {@code useEffect(fn, ONCE)}. */
    protected final void useEffect(Runnable effect, Object... deps) {
        Hooks.current().useEffect(() -> { effect.run(); return null; }, deps);
    }

    /** Effect with cleanup: {@code useEffect(() -> { sub(); return () -> unsub(); }, id)}. */
    protected final void useEffect(Effect effect, Object... deps) { Hooks.current().useEffect(effect, deps); }

    /**
     * Puts the title and tags of {@code head} into {@code <head>}: on the server while the page is rendered, in the browser as an effect
     * (so it follows navigation). The deepest component wins, so a page overrides its layout. See {@link Head}.
     */
    protected final void useHead(Head head) {
        if (HeadCollector.collecting()) { HeadCollector.add(head); return; }
        int depth = Hooks.current().depth();
        Hooks.current().useEffect(() -> {
            HeadHost host = HeadHost.current();
            return host == null ? null : host.apply(head, depth);
        }, new Object[] { head });
    }

    /** Recomputed on every render. */
    protected final <T> T useMemo(Supplier<T> compute) { return useMemo(compute, (Object[]) null); }

    /** Recomputed when a dep changes; {@code useMemo(fn, ONCE)} computes once. */
    protected final <T> T useMemo(Supplier<T> compute, Object... deps) { return Hooks.current().useMemo(compute, deps); }

    protected final <T> Ref<T> useRef(T initial) { return Hooks.current().useRef(initial); }

    /** Value of the nearest {@code context.provide(...)} above this component, or the context's default. */
    protected final <T> T useContext(Context<T> context) { return Hooks.current().useContext(context); }

    /**
     * Whether a render with {@code next} props can be skipped because it would give the same result as the
     * one with {@code prev}. Default: never skip. See {@link MemoComponent}.
     */
    protected boolean arePropsEqual(P prev, P next) { return false; }

    /** Untyped entry point for reconcilers, see {@link #renderWith}. */
    @SuppressWarnings("unchecked")
    public final boolean propsEqualWith(Object prev, Object next) { return arePropsEqual((P) prev, (P) next); }

    // ---------------------------------------------------------------- error boundaries

    /**
     * Override to return true to make this component an error boundary: an exception thrown while rendering
     * (or in a useEffect) of anything below it replaces its subtree by {@link #renderError}. It does not catch
     * its own errors, nor errors in event handlers. See {@link ErrorBoundary} for a ready-made one.
     */
    protected boolean isErrorBoundary() { return false; }

    /** What a boundary shows instead of its subtree. {@code reset} clears the error and renders normally again. */
    protected Element renderError(P props, Throwable error, Runnable reset) { return null; }

    /** Called after the fallback was committed. {@code componentStack} reads {@code Inner > Outer}. */
    protected void didCatch(P props, Throwable error, String componentStack) {}

    /** The error state is also cleared when a render with {@code next} props returns true here. */
    protected boolean shouldResetError(P prev, P next) { return false; }

    public final boolean catchesErrors() { return isErrorBoundary(); }

    @SuppressWarnings("unchecked")
    public final Element renderErrorWith(Object props, Throwable error, Runnable reset) {
        return renderError((P) props, error, reset);
    }

    @SuppressWarnings("unchecked")
    public final void didCatchWith(Object props, Throwable error, String componentStack) {
        didCatch((P) props, error, componentStack);
    }

    @SuppressWarnings("unchecked")
    public final boolean resetsErrorWith(Object prev, Object next) { return shouldResetError((P) prev, (P) next); }
}

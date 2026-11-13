package jreact;

import java.util.function.Supplier;

/** The hook dispatcher of the component that is currently rendering (React's ReactCurrentDispatcher). */
public final class Hooks {
    private Hooks() {}

    public interface Dispatcher {
        <T> State<T> useState(T initial);
        void useEffect(Effect effect, Object[] deps);
        <T> T useMemo(Supplier<T> compute, Object[] deps);
        <T> Ref<T> useRef(T initial);
        <T> T useContext(Context<T> context);

        /** How deep in the component tree the rendering component is (the root is 0). Used to let the deepest {@link Head} win. */
        default int depth() { return 0; }
    }

    private static Dispatcher current;

    /** The dispatcher of the rendering component: custom hooks ({@code useNavigate()}...) are static methods that call it. */
    public static Dispatcher current() {
        if (current == null) throw new IllegalStateException("hooks can only be called inside a component's render()");
        return current;
    }

    /** Installs a dispatcher and returns the previous one (renderers call this around render()). */
    public static Dispatcher swap(Dispatcher next) {
        Dispatcher prev = current;
        current = next;
        return prev;
    }
}

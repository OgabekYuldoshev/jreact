package jreact;

import java.util.List;

/**
 * A value that any descendant can read without passing it down as props (React's createContext).
 * Declare it once, provide it high in the tree, read it with {@code useContext}:
 *
 * <pre>{@code
 * public static final Context<Theme> THEME = Context.create(Theme.LIGHT);
 *
 * // a provider is an ordinary component, so it works from .jrx as <ThemeProvider value={...}>...</ThemeProvider>
 * public class ThemeProvider extends Component<ThemeProvider.Props> {
 *     public record Props(Theme value, List<Object> children) {}
 *     public Element render(Props p) { return THEME.provide(p.value(), p.children()); }
 * }
 *
 * Theme t = useContext(THEME);   // inside any component below it
 * }</pre>
 *
 * A component that reads a context re-renders when the nearest provider's value changes ({@code equals}),
 * even if a {@link MemoComponent} between them did not.
 */
public final class Context<T> {

    private final T defaultValue;
    private final String name;

    private Context(T defaultValue, String name) { this.defaultValue = defaultValue; this.name = name; }

    /** The value read by components that have no provider above them. */
    public static <T> Context<T> create(T defaultValue) { return new Context<>(defaultValue, null); }

    /** Same, with a name that shows up in {@code toString} (debugging). */
    public static <T> Context<T> create(T defaultValue, String name) { return new Context<>(defaultValue, name); }

    public T defaultValue() { return defaultValue; }

    /** Provides {@code value} to everything inside {@code children}. */
    public Element provide(T value, List<Object> children) { return Jsx.provider(this, value, null, children); }

    public Element provide(T value, Object... children) { return Jsx.provider(this, value, null, Jsx.children(children)); }

    @Override public String toString() { return "Context(" + (name != null ? name : defaultValue) + ")"; }
}

package jreact;

import java.util.List;
import java.util.function.Supplier;

/** Immutable description of UI — what JSX compiles to (React's ReactElement). */
public final class Element {

    public enum Kind { HOST, COMPONENT, FRAGMENT, PROVIDER }

    private final Kind kind;
    private final String tag;                                   // HOST
    private final Class<?> type;                                // COMPONENT: identity for reconciliation
    private final Supplier<? extends Component<?>> factory;     // COMPONENT
    private final Object props;                                 // COMPONENT
    private final Attrs attrs;                                  // HOST
    private final Object key;
    private final List<Object> children;                        // HOST, FRAGMENT, PROVIDER
    private final Context<?> context;                           // PROVIDER
    private final Object contextValue;                          // PROVIDER

    private Element(Kind kind, String tag, Class<?> type, Supplier<? extends Component<?>> factory,
                    Object props, Attrs attrs, Object key, List<Object> children,
                    Context<?> context, Object contextValue) {
        this.kind = kind; this.tag = tag; this.type = type; this.factory = factory;
        this.props = props; this.attrs = attrs; this.key = key; this.children = children;
        this.context = context; this.contextValue = contextValue;
    }

    static Element host(String tag, Object key, Attrs attrs, List<Object> children) {
        return new Element(Kind.HOST, tag, null, null, null, attrs, key, children, null, null);
    }

    static Element component(Class<?> type, Supplier<? extends Component<?>> factory, Object key, Object props) {
        return new Element(Kind.COMPONENT, null, type, factory, props, null, key, List.of(), null, null);
    }

    static Element fragment(Object key, List<Object> children) {
        return new Element(Kind.FRAGMENT, null, null, null, null, null, key, children, null, null);
    }

    static Element provider(Context<?> context, Object value, Object key, List<Object> children) {
        return new Element(Kind.PROVIDER, null, null, null, null, null, key, children, context, value);
    }

    public Kind kind() { return kind; }
    public String tag() { return tag; }
    public Class<?> type() { return type; }
    public Supplier<? extends Component<?>> factory() { return factory; }
    public Object props() { return props; }
    public Attrs attrs() { return attrs; }
    public Object key() { return key; }
    public List<Object> children() { return children; }
    public Context<?> context() { return context; }
    public Object contextValue() { return contextValue; }

    /** Same "type" in React's sense: the reconciler reuses a fiber only when this is true. */
    public boolean sameType(Element other) {
        if (other == null || kind != other.kind) return false;
        return switch (kind) {
            case HOST -> tag.equals(other.tag);
            case COMPONENT -> type == other.type;
            case FRAGMENT -> true;
            case PROVIDER -> context == other.context;
        };
    }

    @Override public String toString() {
        return switch (kind) {
            case HOST -> "<" + tag + ">";
            case COMPONENT -> "<" + type.getSimpleName() + ">";
            case FRAGMENT -> "<>";
            case PROVIDER -> "<" + context + ".Provider>";
        };
    }
}

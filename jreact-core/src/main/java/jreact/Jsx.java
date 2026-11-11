package jreact;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Runtime target of compiled .jsxj code (React's jsx()/createElement). */
public final class Jsx {
    private Jsx() {}

    public static Element el(String tag, Object key, Attrs attrs, Object... children) {
        return Element.host(tag, key, attrs == null ? Attrs.EMPTY : attrs, list(children));
    }

    public static <P, C extends Component<P>> Element c(Class<C> type, Supplier<C> factory, Object key, P props) {
        return Element.component(type, factory, key, props);
    }

    public static Element fragment(Object key, Object... children) {
        return Element.fragment(key, list(children));
    }

    /** What {@link Context#provide} compiles to. */
    public static <T> Element provider(Context<T> context, T value, Object key, List<Object> children) {
        return Element.provider(context, value, key, children);
    }

    /** Value of a component's {@code List<Object> children} prop. Items may be null. */
    public static List<Object> children(Object... children) {
        return list(children);
    }

    private static List<Object> list(Object[] items) {
        return items.length == 0 ? List.of() : Collections.unmodifiableList(Arrays.asList(items.clone()));
    }
}

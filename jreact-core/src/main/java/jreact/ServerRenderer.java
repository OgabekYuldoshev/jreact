package jreact;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Renders an element tree to an HTML string once (React's renderToString): state keeps its
 * initial value and effects never run. Used for testing on the JVM and for server rendering.
 */
public final class ServerRenderer {
    private ServerRenderer() {}

    private static final Set<String> VOID_TAGS =
            Set.of("area", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr");

    public static String renderToString(Object node) {
        StringBuilder sb = new StringBuilder();
        render(node, sb);
        return sb.toString();
    }

    private static void render(Object node, StringBuilder sb) {
        if (node == null || node instanceof Boolean) return;
        if (node instanceof Iterable<?> it) { for (Object o : it) render(o, sb); return; }
        if (node instanceof Object[] arr) { for (Object o : arr) render(o, sb); return; }
        if (!(node instanceof Element e)) { escape(String.valueOf(node), sb); return; }

        switch (e.kind()) {
            case FRAGMENT -> render(e.children(), sb);
            case COMPONENT -> {
                Component<?> instance = e.factory().get();
                Hooks.Dispatcher prev = Hooks.swap(new OneShotDispatcher());
                Element out;
                try { out = instance.renderWith(e.props()); } finally { Hooks.swap(prev); }
                render(out, sb);
            }
            case HOST -> {
                sb.append('<').append(e.tag());
                for (Map.Entry<String, Object> a : e.attrs().values().entrySet()) {
                    Object v = a.getValue();
                    if (v == null || Boolean.FALSE.equals(v)) continue;
                    sb.append(' ').append(htmlName(a.getKey()));
                    if (!Boolean.TRUE.equals(v)) { sb.append("=\""); escape(String.valueOf(v), sb); sb.append('"'); }
                }
                sb.append('>');
                if (VOID_TAGS.contains(e.tag())) return;
                render(e.children(), sb);
                sb.append("</").append(e.tag()).append('>');
            }
        }
    }

    private static String htmlName(String name) {
        return switch (name) {
            case "className" -> "class";
            case "htmlFor" -> "for";
            default -> name;
        };
    }

    private static void escape(String s, StringBuilder sb) {
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
    }

    private static final class OneShotDispatcher implements Hooks.Dispatcher {
        @Override public <T> State<T> useState(T initial) {
            return new State<>() {
                public T get() { return initial; }
                public void set(T value) { }
                public void update(java.util.function.UnaryOperator<T> fn) { }
            };
        }
        @Override public void useEffect(Effect effect, Object[] deps) { }
        @Override public <T> T useMemo(Supplier<T> compute, Object[] deps) { return compute.get(); }
        @Override public <T> Ref<T> useRef(T initial) { return new Ref<>(initial); }
    }
}

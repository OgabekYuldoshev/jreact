package jreact;

import java.util.HashMap;
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
        render(node, sb, new HashMap<>(), new int[1]);
        return sb.toString();
    }

    /** {@code provided}: the context values in scope at this point of the tree. */
    private static void render(Object node, StringBuilder sb, Map<Context<?>, Object> provided, int[] textEnd) {
        if (node == null || node instanceof Boolean) return;
        if (node instanceof Iterable<?> it) { for (Object o : it) render(o, sb, provided, textEnd); return; }
        if (node instanceof Object[] arr) { for (Object o : arr) render(o, sb, provided, textEnd); return; }
        if (!(node instanceof Element e)) {
            String text = String.valueOf(node);
            if (text.isEmpty()) return;
            // two text nodes next to each other would parse back as one: a comment keeps them apart (hydration needs that)
            if (textEnd[0] > 0 && sb.length() == textEnd[0]) sb.append("<!-- -->");
            escape(text, sb);
            textEnd[0] = sb.length();
            return;
        }

        switch (e.kind()) {
            case FRAGMENT -> render(e.children(), sb, provided, textEnd);
            case PROVIDER -> {
                boolean had = provided.containsKey(e.context());
                Object prevValue = provided.put(e.context(), e.contextValue());
                try { render(e.children(), sb, provided, textEnd); }
                finally { if (had) provided.put(e.context(), prevValue); else provided.remove(e.context()); }
            }
            case COMPONENT -> {
                Component<?> instance = e.factory().get();
                Hooks.Dispatcher prev = Hooks.swap(new OneShotDispatcher(provided));
                Element out;
                try { out = instance.renderWith(e.props()); } finally { Hooks.swap(prev); }
                render(out, sb, provided, textEnd);
            }
            case HOST -> {
                if (!Safety.isValidTagName(e.tag()) || Safety.isBlockedTag(e.tag())) {
                    throw new IllegalArgumentException("<" + e.tag() + "> cannot be rendered: "
                            + (Safety.isBlockedTag(e.tag()) ? "script elements are not supported" : "not a valid tag name"));
                }
                sb.append('<').append(e.tag());
                for (Map.Entry<String, Object> a : e.attrs().values().entrySet()) {
                    String name = a.getKey();
                    Object v = a.getValue();
                    if (v == null || Boolean.FALSE.equals(v)) continue;
                    if (!Safety.isValidAttributeName(name) || Safety.isInlineHandlerAttribute(name)) continue;
                    if (name.equals("style") && !(v instanceof CharSequence)) {
                        v = Css.toDeclarations(v);
                        if (((String) v).isEmpty()) continue;
                    } else if (Safety.isUrlAttribute(name) && Safety.isDangerousUrl(v)) {
                        v = Safety.BLOCKED_URL;
                    }
                    sb.append(' ').append(htmlName(name));
                    if (!Boolean.TRUE.equals(v)) { sb.append("=\""); escape(String.valueOf(v), sb); sb.append('"'); }
                }
                sb.append('>');
                if (VOID_TAGS.contains(e.tag())) return;
                render(e.children(), sb, provided, textEnd);
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
        private final Map<Context<?>, Object> provided;

        OneShotDispatcher(Map<Context<?>, Object> provided) { this.provided = provided; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T useContext(Context<T> context) {
            return provided.containsKey(context) ? (T) provided.get(context) : context.defaultValue();
        }
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

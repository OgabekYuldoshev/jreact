package jreact;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Turns a JSX {@code style} object (a Map of camelCase name to value) into CSS. Shared by the DOM host and the server renderer. */
public final class Css {
    private Css() {}

    private static final Set<String> UNITLESS = Set.of(
            "opacity", "z-index", "flex", "flex-grow", "flex-shrink", "font-weight", "line-height", "order",
            "zoom", "orphans", "widows", "column-count", "fill-opacity", "stroke-opacity", "tab-size");

    /** CSS names to CSS values, in order. Null, boolean and empty entries are dropped. */
    public static Map<String, String> styleMap(Object style) {
        Map<String, String> out = new LinkedHashMap<>();
        if (style instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() == null || e.getValue() instanceof Boolean || e.getValue().toString().isEmpty()) continue;
                String css = cssName(String.valueOf(e.getKey()));
                out.put(css, cssValue(css, e.getValue()));
            }
        }
        return out;
    }

    /**
     * {@code color: red; background-color: #fff}, for HTML output. Like {@code style.setProperty} in the browser, a
     * declaration whose value would end early ({@code "red; position: fixed"}) is dropped, not split.
     */
    public static String toDeclarations(Object style) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : styleMap(style).entrySet()) {
            if (!Safety.isValidAttributeName(e.getKey().startsWith("--") ? e.getKey().substring(2) : e.getKey())) continue;
            if (sb.length() > 0) sb.append(' ');
            if (!isSingleValue(e.getValue())) continue;
            sb.append(e.getKey()).append(": ").append(e.getValue()).append(';');
        }
        return sb.toString();
    }

    /** No {@code ;}, {@code {} or {@code }} outside quotes and parentheses ({@code url(data:image/png;base64,...)} is fine). */
    private static boolean isSingleValue(String value) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) { if (c == '\\') i++; else if (c == quote) quote = 0; continue; }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth <= 0 && (c == ';' || c == '{' || c == '}')) return false;
        }
        return true;
    }

    /** {@code backgroundColor} to {@code background-color}, {@code WebkitX} to {@code -webkit-x}, {@code --var} stays. */
    public static String cssName(String name) {
        if (name.startsWith("--")) return name;
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('-').append(Character.toLowerCase(c)); else sb.append(c);
        }
        return sb.toString();
    }

    /** Plain numbers get "px" (except 0 and unitless properties), like React. */
    public static String cssValue(String cssName, Object value) {
        if (value instanceof Number n && !cssName.startsWith("--")) {
            if (n.doubleValue() == 0 || UNITLESS.contains(cssName)) return String.valueOf(n);
            return n + "px";
        }
        return String.valueOf(value);
    }
}

package jreact.reconciler;

import jreact.Element;
import jreact.Jsx;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dev-mode checks (React's development warnings). They never change behaviour, they only report mistakes
 * that otherwise show up as odd state loss or slow lists. Each distinct problem is reported once.
 */
final class Diagnostics {

    private boolean enabled = true;
    private Consumer<String> handler = System.err::println;
    private final Set<String> reported = new HashSet<>();

    void setEnabled(boolean enabled) { this.enabled = enabled; }

    void setHandler(Consumer<String> handler) { this.handler = handler; }

    /** Looks at the children a fiber is about to reconcile. */
    void checkChildren(Fiber owner, List<Object> children) {
        if (enabled) checkKeys(owner, children, false);
    }

    /** {@code isList}: the items come from a data-driven list, so each element needs a key. */
    private void checkKeys(Fiber owner, List<?> items, boolean isList) {
        Set<String> keys = null;
        for (Object item : items) {
            if (item instanceof List<?> nested) {
                if (!Jsx.isStaticChildren(nested)) checkKeys(owner, nested, true);
                else checkKeys(owner, nested, false);
                continue;
            }
            if (!(item instanceof Element e)) continue;
            if (e.key() == null) {
                if (isList) {
                    warn("missing-key " + e, "Each child in a list should have a unique \"key\" prop, but " + e
                            + " has none. Keys let the reconciler keep state and DOM nodes when the list changes.", owner);
                }
                continue;
            }
            if (keys == null) keys = new HashSet<>();
            String key = String.valueOf(e.key());
            if (!keys.add(key)) {
                warn("duplicate-key " + key, "Encountered two children with the same key `" + key + "`. Keys must be "
                        + "unique among siblings, otherwise items can be duplicated or dropped on updates.", owner);
            }
        }
    }

    private void warn(String problem, String message, Fiber owner) {
        String where = componentStack(owner);
        if (!reported.add(problem + " @ " + where)) return;
        handler.accept("Warning: " + message + (where.isEmpty() ? "" : "\n    in " + where));
    }

    /** {@code Card > TodoApp}: the components around the problem, innermost first. */
    private static String componentStack(Fiber from) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Fiber f = from; f != null && depth < 6; f = f.ret) {
            if (f.tag != Fiber.Tag.COMPONENT) continue;
            if (sb.length() > 0) sb.append(" > ");
            sb.append(((Class<?>) f.type).getSimpleName());
            depth++;
        }
        return sb.toString();
    }
}

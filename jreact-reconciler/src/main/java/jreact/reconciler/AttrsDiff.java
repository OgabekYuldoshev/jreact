package jreact.reconciler;

import jreact.Attrs;
import jreact.EventHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** What changed between two {@link Attrs} of the same host element (React's updatePayload). */
public final class AttrsDiff {

    private final Map<String, Object> attrs = new LinkedHashMap<>();
    private final Map<String, EventHandler> events = new LinkedHashMap<>();

    /** Changed or added attributes. A {@code null} value means the attribute was removed. */
    public Map<String, Object> attrs() { return Collections.unmodifiableMap(attrs); }

    /** New or replaced handlers. A {@code null} value means the handler was removed. */
    public Map<String, EventHandler> events() { return Collections.unmodifiableMap(events); }

    public boolean isEmpty() { return attrs.isEmpty() && events.isEmpty(); }

    static AttrsDiff between(Attrs prev, Attrs next) {
        AttrsDiff d = new AttrsDiff();
        for (String k : prev.values().keySet()) {
            if (!next.values().containsKey(k)) d.attrs.put(k, null);
        }
        for (Map.Entry<String, Object> e : next.values().entrySet()) {
            String k = e.getKey();
            if (!prev.values().containsKey(k) || !Objects.equals(prev.values().get(k), e.getValue())) {
                d.attrs.put(k, e.getValue());
            }
        }
        for (String k : prev.events().keySet()) {
            if (!next.events().containsKey(k)) d.events.put(k, null);
        }
        for (Map.Entry<String, EventHandler> e : next.events().entrySet()) {
            if (prev.events().get(e.getKey()) != e.getValue()) d.events.put(e.getKey(), e.getValue());
        }
        return d;
    }
}

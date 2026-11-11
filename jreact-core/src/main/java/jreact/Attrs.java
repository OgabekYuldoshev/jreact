package jreact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Props of a host element (div, input, ...): plain attributes, event handlers and a ref. */
public final class Attrs {

    public static final Attrs EMPTY = new Attrs();

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, EventHandler> events = new LinkedHashMap<>();
    private Ref<?> ref;

    public Attrs attr(String name, Object value) { values.put(name, value); return this; }

    public Attrs on(String name, EventHandler handler) { events.put(name, handler); return this; }

    public Attrs ref(Ref<?> ref) { this.ref = ref; return this; }

    public Attrs spread(Attrs other) {
        if (other != null) {
            values.putAll(other.values);
            events.putAll(other.events);
            if (other.ref != null) ref = other.ref;
        }
        return this;
    }

    public Map<String, Object> values() { return Collections.unmodifiableMap(values); }
    public Map<String, EventHandler> events() { return Collections.unmodifiableMap(events); }
    public Ref<?> ref() { return ref; }
}

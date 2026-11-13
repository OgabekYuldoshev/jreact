package jreact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Props of a host element (div, input, ...): plain attributes, event handlers and a ref.
 * <p>Stored in small parallel arrays in insertion order (an element has a handful of attributes: a linear scan beats hashing, and an
 * element without attributes allocates nothing). {@link #values()} and {@link #events()} give the usual map views, built on demand;
 * the reconciler and the DOM host use the indexed accessors instead.
 */
public final class Attrs {

    public static final Attrs EMPTY = new Attrs();

    private static final String[] NO_NAMES = new String[0];
    private static final Object[] NO_VALUES = new Object[0];
    private static final EventHandler[] NO_HANDLERS = new EventHandler[0];

    private String[] names = NO_NAMES;
    private Object[] vals = NO_VALUES;
    private int count;

    private String[] eventNames = NO_NAMES;
    private EventHandler[] handlers = NO_HANDLERS;
    private int eventCount;

    private Ref<?> ref;
    private Map<String, Object> valuesView;
    private Map<String, EventHandler> eventsView;

    public Attrs attr(String name, Object value) {
        int i = attrIndex(name, -1);
        if (i >= 0) vals[i] = value;
        else {
            if (count == names.length) {
                int cap = count == 0 ? 4 : count * 2;
                String[] n2 = new String[cap];
                Object[] v2 = new Object[cap];
                System.arraycopy(names, 0, n2, 0, count);      // not Arrays.copyOf: the generic one pulls reflection into the TeaVM bundle
                System.arraycopy(vals, 0, v2, 0, count);
                names = n2;
                vals = v2;
            }
            names[count] = name;
            vals[count++] = value;
        }
        valuesView = null;
        return this;
    }

    public Attrs on(String name, EventHandler handler) {
        int i = indexOf(eventNames, eventCount, name, -1);
        if (i >= 0) handlers[i] = handler;
        else {
            if (eventCount == eventNames.length) {
                int cap = eventCount == 0 ? 2 : eventCount * 2;
                String[] n2 = new String[cap];
                EventHandler[] h2 = new EventHandler[cap];
                System.arraycopy(eventNames, 0, n2, 0, eventCount);
                System.arraycopy(handlers, 0, h2, 0, eventCount);
                eventNames = n2;
                handlers = h2;
            }
            eventNames[eventCount] = name;
            handlers[eventCount++] = handler;
        }
        eventsView = null;
        return this;
    }

    public Attrs ref(Ref<?> ref) { this.ref = ref; return this; }

    public Attrs spread(Attrs other) {
        if (other != null) {
            for (int i = 0; i < other.count; i++) attr(other.names[i], other.vals[i]);
            for (int i = 0; i < other.eventCount; i++) on(other.eventNames[i], other.handlers[i]);
            if (other.ref != null) ref = other.ref;
        }
        return this;
    }

    public Ref<?> ref() { return ref; }

    // ---- indexed access (insertion order; what the reconciler and the DOM host use)

    public int attrSize() { return count; }
    public String attrName(int i) { return names[i]; }
    public Object attrValue(int i) { return vals[i]; }

    /** Index of the attribute, or -1. {@code hint} is the index to try first (the same attribute usually sits at the same place in the next render). */
    public int attrIndex(String name, int hint) { return indexOf(names, count, name, hint); }

    public int eventSize() { return eventCount; }
    public String eventName(int i) { return eventNames[i]; }
    public EventHandler eventHandler(int i) { return handlers[i]; }
    public int eventIndex(String name, int hint) { return indexOf(eventNames, eventCount, name, hint); }

    private static int indexOf(String[] names, int count, String name, int hint) {
        if (hint >= 0 && hint < count && names[hint] == name) return hint;       // names are nearly always the same literals
        for (int i = 0; i < count; i++) if (names[i] == name) return i;
        for (int i = 0; i < count; i++) if (names[i].equals(name)) return i;
        return -1;
    }

    // ---- map views

    public Map<String, Object> values() {
        if (valuesView == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) m.put(names[i], vals[i]);
            valuesView = Collections.unmodifiableMap(m);
        }
        return valuesView;
    }

    public Map<String, EventHandler> events() {
        if (eventsView == null) {
            Map<String, EventHandler> m = new LinkedHashMap<>();
            for (int i = 0; i < eventCount; i++) m.put(eventNames[i], handlers[i]);
            eventsView = Collections.unmodifiableMap(m);
        }
        return eventsView;
    }
}

package jreact.reconciler;

import jreact.Attrs;
import jreact.EventHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What changed between two {@link Attrs} of the same host element (React's updatePayload).
 * The common result, "nothing changed", is the shared {@link #EMPTY} and allocates nothing.
 */
public final class AttrsDiff {

    static final AttrsDiff EMPTY = new AttrsDiff();

    private String[] attrNames;
    private Object[] attrValues;
    private int attrCount;
    private String[] eventNames;
    private EventHandler[] eventHandlers;
    private int eventCount;

    /** Changed or added attributes, in order. A {@code null} value means the attribute was removed. */
    public int attrCount() { return attrCount; }
    public String attrName(int i) { return attrNames[i]; }
    public Object attrValue(int i) { return attrValues[i]; }

    /** New or replaced handlers, in order. A {@code null} handler means it was removed. */
    public int eventCount() { return eventCount; }
    public String eventName(int i) { return eventNames[i]; }
    public EventHandler eventHandler(int i) { return eventHandlers[i]; }

    public boolean isEmpty() { return attrCount == 0 && eventCount == 0; }

    /** The attribute changes as a map (for tests and tools; the indexed accessors do not allocate). */
    public Map<String, Object> attrs() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < attrCount; i++) m.put(attrNames[i], attrValues[i]);
        return Collections.unmodifiableMap(m);
    }

    public Map<String, EventHandler> events() {
        Map<String, EventHandler> m = new LinkedHashMap<>();
        for (int i = 0; i < eventCount; i++) m.put(eventNames[i], eventHandlers[i]);
        return Collections.unmodifiableMap(m);
    }

    static AttrsDiff between(Attrs prev, Attrs next) {
        if (prev == next) return EMPTY;
        AttrsDiff d = null;
        int pn = prev.attrSize(), nn = next.attrSize();
        for (int i = 0; i < pn; i++) {
            String k = prev.attrName(i);
            if (next.attrIndex(k, i) < 0) { if (d == null) d = new AttrsDiff(); d.addAttr(k, null); }
        }
        for (int i = 0; i < nn; i++) {
            String k = next.attrName(i);
            Object v = next.attrValue(i);
            int j = prev.attrIndex(k, i);
            if (j < 0 || !Objects.equals(prev.attrValue(j), v)) { if (d == null) d = new AttrsDiff(); d.addAttr(k, v); }
        }
        int pe = prev.eventSize(), ne = next.eventSize();
        for (int i = 0; i < pe; i++) {
            String k = prev.eventName(i);
            if (next.eventIndex(k, i) < 0) { if (d == null) d = new AttrsDiff(); d.addEvent(k, null); }
        }
        for (int i = 0; i < ne; i++) {
            String k = next.eventName(i);
            EventHandler h = next.eventHandler(i);
            int j = prev.eventIndex(k, i);
            if (j < 0 || prev.eventHandler(j) != h) { if (d == null) d = new AttrsDiff(); d.addEvent(k, h); }
        }
        return d == null ? EMPTY : d;
    }

    private void addAttr(String name, Object value) {
        if (attrNames == null) { attrNames = new String[4]; attrValues = new Object[4]; }
        else if (attrCount == attrNames.length) {
            String[] n2 = new String[attrCount * 2];
            Object[] v2 = new Object[attrCount * 2];
            System.arraycopy(attrNames, 0, n2, 0, attrCount);     // not Arrays.copyOf: the generic one pulls reflection into the TeaVM bundle
            System.arraycopy(attrValues, 0, v2, 0, attrCount);
            attrNames = n2;
            attrValues = v2;
        }
        attrNames[attrCount] = name;
        attrValues[attrCount++] = value;
    }

    private void addEvent(String name, EventHandler handler) {
        if (eventNames == null) { eventNames = new String[2]; eventHandlers = new EventHandler[2]; }
        else if (eventCount == eventNames.length) {
            String[] n2 = new String[eventCount * 2];
            EventHandler[] h2 = new EventHandler[eventCount * 2];
            System.arraycopy(eventNames, 0, n2, 0, eventCount);
            System.arraycopy(eventHandlers, 0, h2, 0, eventCount);
            eventNames = n2;
            eventHandlers = h2;
        }
        eventNames[eventCount] = name;
        eventHandlers[eventCount++] = handler;
    }
}

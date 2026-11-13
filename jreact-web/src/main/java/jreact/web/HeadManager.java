package jreact.web;

import jreact.Head;
import jreact.HeadHost;
import org.teavm.jso.JSBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps {@code document.title} and the tags of {@link Head} in step with the components that asked for them. Every component's head is an
 * entry; the page shows the entries merged, deepest component last (so it wins), and when an entry goes away the rest is shown again
 * (the original title comes back when none is left). Tags carry {@code data-jreact-head}, which is also what the server writes, so a
 * server rendered page is taken over without duplicates.
 */
final class HeadManager implements HeadHost {

    private static final class Entry {
        final Head head; final int depth; final int order;
        Entry(Head head, int depth, int order) { this.head = head; this.depth = depth; this.order = order; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int counter;
    private String originalTitle;

    @Override
    public Runnable apply(Head head, int depth) {
        if (originalTitle == null) originalTitle = title();
        Entry e = new Entry(head, depth, counter++);
        entries.add(e);
        render();
        return () -> { entries.remove(e); render(); };
    }

    private void render() {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> a.depth != b.depth ? Integer.compare(a.depth, b.depth) : Integer.compare(a.order, b.order));
        Head merged = Head.EMPTY;
        for (Entry e : sorted) merged = merged.merge(e.head);
        setTitle(merged.title() != null ? merged.title() : originalTitle);
        clearTags();
        for (Head.Tag t : merged.tags()) addTag(t.element(), t.keyAttr(), t.keyValue(), t.valueAttr(), t.value());
    }

    @JSBody(script = "return document.title;")
    private static native String title();

    @JSBody(params = {"title"}, script = "if (title !== null) document.title = title;")
    private static native void setTitle(String title);

    @JSBody(script = "var l = document.head.querySelectorAll('[data-jreact-head]'); for (var i = l.length - 1; i >= 0; i--) l[i].parentNode.removeChild(l[i]);")
    private static native void clearTags();

    @JSBody(params = {"el", "keyAttr", "keyValue", "valueAttr", "value"}, script = "var tag = document.createElement(el); tag.setAttribute(keyAttr, keyValue); tag.setAttribute(valueAttr, value); tag.setAttribute('data-jreact-head', ''); document.head.appendChild(tag);")
    private static native void addTag(String el, String ka, String kv, String va, String v);
}

package jreact.dom;

import jreact.Attrs;
import jreact.Event;
import jreact.EventHandler;
import jreact.Safety;
import jreact.reconciler.AttrsDiff;
import jreact.reconciler.HostConfig;
import jreact.scheduler.Priority;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The DOM host config: applies what the reconciler decided to a DOM (through {@link DomOps}) and routes events.
 *
 * <p>Events are delegated like in React: handlers are not attached to elements. They are kept in a per-node
 * table, and one listener per event type on the root container finds the target's ancestors, calls their
 * handlers from the inside out and honours {@code stopPropagation()}. Each event runs at the priority of its
 * type, so clicks and typing are never stuck behind a background transition.
 *
 * <p>One host per root container.
 */
public final class DomHost<N> implements HostConfig<N> {

    /** What the host remembers about one element, stored in {@link DomOps#setData}. */
    private static final class NodeData {
        final Map<String, EventHandler> handlers = new HashMap<>();
        Map<String, String> style = new HashMap<>();
    }

    private final DomOps<N> ops;
    private final N container;
    private final Set<String> listening = new HashSet<>();
    private BiConsumer<Priority, Runnable> updateRunner = (priority, body) -> body.run();
    private Consumer<String> warn = System.err::println;
    private final Set<String> warned = new HashSet<>();

    public DomHost(DomOps<N> ops, N container) {
        this.ops = ops;
        this.container = container;
    }

    /** How event handlers are run at a priority: {@code reconciler::runWithPriority}. Default: run directly. */
    public void setUpdateRunner(BiConsumer<Priority, Runnable> runner) { this.updateRunner = runner; }

    /** Where refused input (a blocked URL, an {@code onclick} string...) is reported, once per distinct message. Default: stderr. */
    public void setWarningHandler(Consumer<String> handler) { this.warn = handler; }

    private void warnOnce(String message) { if (warned.add(message)) warn.accept("Warning: " + message); }

    // ---------------------------------------------------------------- HostConfig

    @Override
    public N createInstance(String tag) {
        if (Safety.isBlockedTag(tag)) {
            throw new IllegalArgumentException("<script> elements are not supported: add the script to the page's HTML");
        }
        if (!Safety.isValidTagName(tag)) throw new IllegalArgumentException("not a valid tag name: " + tag);
        return ops.createElement(tag);
    }

    @Override public N createTextInstance(String text) { return ops.createTextNode(text); }

    @Override
    public void finalizeInitialChildren(N instance, Attrs initial) {
        initial.values().forEach((name, value) -> applyAttribute(instance, name, value));
        initial.events().forEach((name, handler) -> setHandler(instance, name, handler));
    }

    @Override public void appendInitialChild(N parent, N child) { ops.appendChild(parent, child); }

    @Override public void appendChild(N parent, N child) { ops.appendChild(parent, child); }

    @Override public void insertBefore(N parent, N child, N before) { ops.insertBefore(parent, child, before); }

    @Override public void removeChild(N parent, N child) { ops.removeChild(parent, child); }

    @Override
    public void commitUpdate(N instance, AttrsDiff diff) {
        diff.attrs().forEach((name, value) -> applyAttribute(instance, name, value));
        diff.events().forEach((name, handler) -> setHandler(instance, name, handler));
    }

    @Override public void commitTextUpdate(N textInstance, String oldText, String newText) {
        ops.setTextContent(textInstance, newText);
    }

    // ---------------------------------------------------------------- attributes

    private void applyAttribute(N node, String name, Object value) {
        if (name.equals("style")) { applyStyle(node, value); return; }
        if (DomAttributes.isProperty(name)) {
            ops.setProperty(node, name, DomAttributes.propertyValue(name, value));
            return;
        }
        String attribute = DomAttributes.attributeName(name);
        if (!Safety.isValidAttributeName(attribute)) {
            warnOnce("attribute name \"" + name + "\" is not a valid HTML attribute name and was ignored");
            return;
        }
        if (Safety.isInlineHandlerAttribute(attribute)) {
            warnOnce("\"" + name + "\" was ignored: event handlers are functions (onClick={e -> ...}), not attribute strings");
            return;
        }
        if (value != null && Safety.isUrlAttribute(attribute) && Safety.isDangerousUrl(value)) {
            warnOnce("a javascript: URL in \"" + name + "\" was blocked");
            value = Safety.BLOCKED_URL;
        }
        boolean isBoolean = DomAttributes.isBooleanAttribute(attribute);
        if (value == null || (isBoolean && Boolean.FALSE.equals(value))) {
            ops.removeAttribute(node, attribute);
        } else if (isBoolean) {
            ops.setAttribute(node, attribute, "");
        } else {
            ops.setAttribute(node, attribute, String.valueOf(value));
        }
    }

    private void applyStyle(N node, Object value) {
        NodeData data = data(node);
        if (value instanceof CharSequence css) {            // style="color: red": the browser owns the parsing
            ops.setAttribute(node, "style", css.toString());
            data.style = new HashMap<>();
            return;
        }
        Map<String, String> next = DomAttributes.styleMap(value);
        for (String cssName : data.style.keySet()) {
            if (!next.containsKey(cssName)) ops.setStyleProperty(node, cssName, null);
        }
        for (Map.Entry<String, String> e : next.entrySet()) {
            if (!e.getValue().equals(data.style.get(e.getKey()))) ops.setStyleProperty(node, e.getKey(), e.getValue());
        }
        data.style = next;
    }

    private NodeData data(N node) {
        if (ops.getData(node) instanceof NodeData d) return d;
        NodeData d = new NodeData();
        ops.setData(node, d);
        return d;
    }

    // ---------------------------------------------------------------- events

    private void setHandler(N node, String name, EventHandler handler) {
        if (!name.startsWith("on") || name.length() < 3) return;
        if (handler == null) {
            if (ops.getData(node) instanceof NodeData d) d.handlers.remove(name);
            return;
        }
        data(node).handlers.put(name, handler);
        for (String type : DomEvents.nativeTypes(name)) {
            if (listening.add(type)) ops.addEventListener(container, type, this::dispatch);
        }
    }

    private void dispatch(NativeEvent<N> nativeEvent) {
        List<String> names = DomEvents.handlerNames(nativeEvent.type(), nativeEvent.targetTag(), nativeEvent.targetType());
        updateRunner.accept(DomEvents.priority(nativeEvent.type()), () -> {
            Synthetic event = new Synthetic(nativeEvent);
            for (N node = nativeEvent.target(); node != null && node != container && !event.stopped;
                 node = ops.parentNode(node)) {
                if (!(ops.getData(node) instanceof NodeData d)) continue;
                for (String name : names) {
                    EventHandler handler = d.handlers.get(name);
                    if (handler != null) handler.handle(event);
                }
            }
        });
    }

    private static final class Synthetic implements Event {
        private final NativeEvent<?> source;
        boolean stopped;

        Synthetic(NativeEvent<?> source) { this.source = source; }

        @Override public String type() { return source.type(); }
        @Override public String value() { return source.value(); }
        @Override public boolean checked() { return source.checked(); }
        @Override public void preventDefault() { source.preventDefault(); }
        @Override public void stopPropagation() { stopped = true; }
        @Override public boolean modified() { return source.modified(); }
    }
}

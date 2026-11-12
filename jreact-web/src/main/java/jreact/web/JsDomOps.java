package jreact.web;

import jreact.dom.DomOps;
import jreact.dom.NativeEvent;
import org.teavm.jso.JSBody;

import java.util.function.Consumer;

/** {@link DomOps} on the real browser DOM. */
public final class JsDomOps implements DomOps<JsNode> {

    private final JsDocument document = document();

    @JSBody(script = "return document;")
    private static native JsDocument document();

    @Override public JsNode createElement(String tag) { return document.createElement(tag); }

    @Override public JsNode createTextNode(String text) { return document.createTextNode(text); }

    @Override public void setAttribute(JsNode element, String name, String value) { element.setAttribute(name, value); }

    @Override public void removeAttribute(JsNode element, String name) { element.removeAttribute(name); }

    @Override
    public void setProperty(JsNode element, String name, Object value) {
        if (value instanceof Boolean b) setBooleanProperty(element, name, b);
        else setStringProperty(element, name, String.valueOf(value));
    }

    // only write when different: assigning an input's value moves the caret even if the text is the same
    @JSBody(params = {"e", "n", "v"}, script = "if (e[n] !== v) e[n] = v;")
    private static native void setStringProperty(JsNode element, String name, String value);

    @JSBody(params = {"e", "n", "v"}, script = "if (e[n] !== v) e[n] = v;")
    private static native void setBooleanProperty(JsNode element, String name, boolean value);

    @Override public void setStyleProperty(JsNode element, String cssName, String value) { style(element, cssName, value); }

    @JSBody(params = {"e", "n", "v"}, script = "if (v === null) e.style.removeProperty(n); else e.style.setProperty(n, v);")
    private static native void style(JsNode element, String cssName, String value);

    @Override public void setTextContent(JsNode textNode, String text) { textNode.setTextContent(text); }

    @Override public void appendChild(JsNode parent, JsNode child) { parent.appendChild(child); }

    @Override public void insertBefore(JsNode parent, JsNode child, JsNode before) { parent.insertBefore(child, before); }

    @Override public void removeChild(JsNode parent, JsNode child) { parent.removeChild(child); }

    @Override public JsNode parentNode(JsNode node) { return node.getParentNode(); }

    @Override public Object getData(JsNode node) { return data(node); }

    @Override public void setData(JsNode node, Object data) { storeData(node, data); }

    @JSBody(params = {"n"}, script = "return n.__jreact === undefined ? null : n.__jreact;")
    private static native Object data(JsNode node);

    @JSBody(params = {"n", "d"}, script = "n.__jreact = d;")
    private static native void storeData(JsNode node, Object data);

    @Override
    public void addEventListener(JsNode container, String type, Consumer<NativeEvent<JsNode>> listener) {
        // scroll does not bubble: only the capture phase of an ancestor sees it
        container.addEventListener(type, event -> listener.accept(new JsNativeEvent(event)), type.equals("scroll"));
    }

    private static final class JsNativeEvent implements NativeEvent<JsNode> {
        private final JsEvent event;

        JsNativeEvent(JsEvent event) { this.event = event; }

        @Override public String type() { return event.getType(); }
        @Override public JsNode target() { return event.getTarget(); }
        @Override public String targetTag() { return tagName(event.getTarget()); }
        @Override public String targetType() { return typeAttr(event.getTarget()); }
        @Override public String value() { return value(event.getTarget()); }
        @Override public boolean checked() { return checked(event.getTarget()); }
        @Override public void preventDefault() { event.preventDefault(); }
        @Override public boolean modified() { return modifiedClick(event); }
        @Override public String formData() { return formFields(event.getTarget()); }

        // the form itself (submit event) or the form that owns the target; files are skipped
        @JSBody(params = {"t"}, script = "var f = t && (t.tagName === 'FORM' ? t : t.form || (t.closest && t.closest('form'))); if (!f) return '';"
                + "var p = new URLSearchParams(); new FormData(f).forEach(function (v, k) { if (typeof v === 'string') p.append(k, v); }); return p.toString();")
        private static native String formFields(JsNode target);

        @JSBody(params = {"e"}, script = "return !!(e.ctrlKey || e.metaKey || e.shiftKey || e.altKey || (e.button || 0) !== 0);")
        private static native boolean modifiedClick(JsEvent event);

        @JSBody(params = {"t"}, script = "return t.tagName ? t.tagName.toLowerCase() : null;")
        private static native String tagName(JsNode target);

        @JSBody(params = {"t"}, script = "return typeof t.type === 'string' ? t.type : null;")
        private static native String typeAttr(JsNode target);

        @JSBody(params = {"t"}, script = "return (t.value === undefined || t.value === null) ? null : String(t.value);")
        private static native String value(JsNode target);

        @JSBody(params = {"t"}, script = "return !!t.checked;")
        private static native boolean checked(JsNode target);
    }
}

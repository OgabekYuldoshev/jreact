package jreact.web;

import jreact.dom.DomRoot;
import jreact.dom.EventLoopSchedulerHost;
import org.teavm.jso.JSBody;

/** Browser entry point: {@code JReactWeb.createRoot("root").render(app)}. */
public final class JReactWeb {
    private JReactWeb() {}

    @JSBody(script = "return document;")
    private static native JsDocument document();

    /** Renders into the element with this id. */
    public static DomRoot<JsNode> createRoot(String elementId) {
        JsNode container = document().getElementById(elementId);
        if (container == null) throw new IllegalArgumentException("no element with id '" + elementId + "'");
        return createRoot(container);
    }

    public static DomRoot<JsNode> createRoot(JsNode container) {
        if (dev.jreact.api.Rpc.transport() == null) dev.jreact.api.Rpc.transport(new HttpTransport());     // @ServerApi clients work without setup
        return DomRoot.create(new JsDomOps(), container, new EventLoopSchedulerHost(new JsTimers()));
    }
}

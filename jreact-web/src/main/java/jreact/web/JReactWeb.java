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
        if (jreact.HeadHost.current() == null) jreact.HeadHost.install(new HeadManager());      // useHead() and @Page(title) edit document.head
        preloadServerAnswers();
        return DomRoot.create(new JsDomOps(), container, new EventLoopSchedulerHost(new JsTimers()));
    }

    /**
     * A page rendered by the server carries the answers its loaders got ({@code <script id="__jreact_data" type="application/json">}):
     * the first identical {@code @ServerApi} calls in the browser use them, so hydration starts from the same data and asks the server nothing.
     */
    private static void preloadServerAnswers() {
        JsNode data = document().getElementById("__jreact_data");
        if (data == null) return;
        try {
            if (jreact.Json.parse(data.getTextContent()) instanceof java.util.Map<?, ?> m) {
                java.util.Map<String, Object> answers = new java.util.HashMap<>();
                m.forEach((k, v) -> answers.put((String) k, v));
                dev.jreact.api.Rpc.preload(answers);
            }
        } catch (jreact.Json.ParseException ignored) { /* no preloaded answers: the loaders ask the server */ }
    }
}

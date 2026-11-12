package app;

import org.teavm.jso.JSBody;

final class Native {
    private Native() {}

    @JSBody(script = "return performance.now();")
    static native double now();

    /** Reading a layout property forces style + layout, so the timings include more than script time. */
    @JSBody(script = "return document.body.offsetHeight;")
    static native int layout();

    /** {@code ?dev} in the URL keeps the dev-mode checks (key warnings...) on; the default is production mode. */
    @JSBody(script = "return location.search.indexOf('dev') >= 0;")
    static native boolean devMode();

    @JSBody(params = {"s"}, script = "window.__result = s; document.getElementById('result').textContent = s;")
    static native void publish(String s);
}

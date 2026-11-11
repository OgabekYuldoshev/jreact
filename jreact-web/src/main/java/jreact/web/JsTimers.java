package jreact.web;

import jreact.dom.Timers;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.util.ArrayDeque;

/** {@link Timers} on the browser event loop: performance.now, MessageChannel, setTimeout. */
public final class JsTimers implements Timers {

    @JSFunctor
    private interface Callback extends JSObject {
        void call();
    }

    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final JSObject channel;

    public JsTimers() {
        channel = createChannel(() -> {
            Runnable task = tasks.poll();
            if (task != null) task.run();
        });
    }

    @Override public long now() { return (long) performanceNow(); }

    @Override
    public void postTask(Runnable task) {
        tasks.add(task);
        post(channel);
    }

    @Override public Object setTimeout(Runnable task, long delayMs) { return timeout(task::run, (double) delayMs); }

    @Override public void clearTimeout(Object handle) { cancelTimeout((Integer) handle); }

    @JSBody(script = "return performance.now();")
    private static native double performanceNow();

    // MessageChannel posts a macrotask without the 4ms clamp of nested setTimeout; fall back where it is missing
    @JSBody(params = {"cb"}, script =
            "var c = {};"
            + "if (typeof MessageChannel !== 'undefined') {"
            + "  var ch = new MessageChannel(); ch.port1.onmessage = function() { cb(); };"
            + "  c.post = function() { ch.port2.postMessage(null); };"
            + "} else { c.post = function() { setTimeout(cb, 0); }; }"
            + "return c;")
    private static native JSObject createChannel(Callback cb);

    @JSBody(params = {"c"}, script = "c.post();")
    private static native void post(JSObject channel);

    @JSBody(params = {"cb", "ms"}, script = "return setTimeout(function() { cb(); }, ms);")
    private static native int timeout(Callback cb, double ms);

    @JSBody(params = {"id"}, script = "clearTimeout(id);")
    private static native void cancelTimeout(int id);
}

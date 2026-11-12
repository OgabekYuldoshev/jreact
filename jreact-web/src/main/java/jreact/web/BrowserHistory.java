package jreact.web;

import jreact.router.History;
import jreact.router.Location;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link History} on the browser's address bar (the History API): real paths like {@code /posts/7}. The server must
 * answer every such path with the app's {@code index.html} (single-page app fallback); if it cannot, use {@link HashHistory}.
 */
public final class BrowserHistory implements History {

    @JSFunctor
    private interface Callback extends JSObject {
        void call();
    }

    private final List<Runnable> listeners = new ArrayList<>();

    public BrowserHistory() { onPopState(this::changed); }

    @Override public Location location() { return Location.parse(url()); }

    @Override public void push(String to) { pushState(to); changed(); }

    @Override public void replace(String to) { replaceState(to); changed(); }

    @Override public void go(int delta) { goBy(delta); }      // the browser fires popstate when it has moved

    @Override
    public Runnable listen(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void changed() { for (Runnable l : new ArrayList<>(listeners)) l.run(); }

    @JSBody(script = "return location.pathname + location.search + location.hash;")
    private static native String url();

    @JSBody(params = {"u"}, script = "history.pushState(null, '', u);")
    private static native void pushState(String url);

    @JSBody(params = {"u"}, script = "history.replaceState(null, '', u);")
    private static native void replaceState(String url);

    @JSBody(params = {"n"}, script = "history.go(n);")
    private static native void goBy(int delta);

    @JSBody(params = {"cb"}, script = "window.addEventListener('popstate', function () { cb(); });")
    private static native void onPopState(Callback callback);
}

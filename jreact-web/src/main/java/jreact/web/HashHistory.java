package jreact.web;

import jreact.router.History;
import jreact.router.Location;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link History} that keeps the path after the {@code #} ({@code index.html#/posts/7}). It works on any static file
 * host, because the server never sees the part after the {@code #}. Links get {@code href="#/posts/7"}.
 */
public final class HashHistory implements History {

    @JSFunctor
    private interface Callback extends JSObject {
        void call();
    }

    private final List<Runnable> listeners = new ArrayList<>();

    public HashHistory() { onPopState(this::changed); }

    @Override public Location location() { return Location.parse(hashPath()); }

    @Override public void push(String to) { pushState("#" + to); changed(); }

    @Override public void replace(String to) { replaceState("#" + to); changed(); }

    @Override public void go(int delta) { goBy(delta); }

    @Override public String href(String to) { return "#" + to; }

    @Override
    public Runnable listen(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void changed() { for (Runnable l : new ArrayList<>(listeners)) l.run(); }

    @JSBody(script = "var h = location.hash; return h.length > 1 ? h.substring(1) : '/';")
    private static native String hashPath();

    @JSBody(params = {"u"}, script = "history.pushState(null, '', u);")
    private static native void pushState(String url);

    @JSBody(params = {"u"}, script = "history.replaceState(null, '', u);")
    private static native void replaceState(String url);

    @JSBody(params = {"n"}, script = "history.go(n);")
    private static native void goBy(int delta);

    // popstate also fires when the user edits the hash by hand or follows a #link; hashchange covers browsers where it does not.
    // When both fire the second notification changes nothing (same location, no re-render).
    @JSBody(params = {"cb"}, script = "window.addEventListener('popstate', function () { cb(); }); window.addEventListener('hashchange', function () { cb(); });")
    private static native void onPopState(Callback callback);
}

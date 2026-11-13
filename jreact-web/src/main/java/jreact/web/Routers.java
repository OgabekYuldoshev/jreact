package jreact.web;

import jreact.router.DataRouter;
import jreact.router.RouteObject;
import org.teavm.jso.JSBody;

import java.util.List;

/**
 * Data routers for the browser: {@code createBrowserRouter} (real paths, needs a single-page-app fallback on the server) and
 * {@code createHashRouter} (works on any static host). Hand the result to {@code <RouterProvider router={...} />}.
 * They also turn on scroll restoration for {@code <ScrollRestoration />}.
 */
public final class Routers {
    private Routers() {}

    public static DataRouter createBrowserRouter(List<RouteObject> routes) { return createBrowserRouter(routes, null); }

    /** @param basename the prefix the app is served under (for example {@code /docs}); null: the server's context path ({@link Http#base()}), if any */
    public static DataRouter createBrowserRouter(List<RouteObject> routes, String basename) {
        if (basename == null) basename = Http.base();
        return withScroll(new DataRouter(routes, new BrowserHistory(), basename));
    }

    public static DataRouter createHashRouter(List<RouteObject> routes) { return withScroll(new DataRouter(routes, new HashHistory(), null)); }

    private static DataRouter withScroll(DataRouter router) {
        manualRestoration();
        router.setScroller(new jreact.router.Scroller() {
            @Override public int[] position() { return new int[]{(int) scrollX(), (int) scrollY()}; }
            @Override public void scrollTo(int x, int y) { scrollToXY(x, y); }
            @Override public void toHash(String hash) { scrollToId(hash.startsWith("#") ? hash.substring(1) : hash); }
        });
        return router;
    }

    @JSBody(script = "if ('scrollRestoration' in history) history.scrollRestoration = 'manual';")
    private static native void manualRestoration();

    @JSBody(script = "return window.scrollX || 0;")
    private static native double scrollX();

    @JSBody(script = "return window.scrollY || 0;")
    private static native double scrollY();

    @JSBody(params = {"x", "y"}, script = "window.scrollTo(x, y);")
    private static native void scrollToXY(int x, int y);

    @JSBody(params = {"id"}, script = "var e = document.getElementById(decodeURIComponent(id)); if (e) e.scrollIntoView(); else window.scrollTo(0, 0);")
    private static native void scrollToId(String id);
}

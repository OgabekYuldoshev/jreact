package jreact.router;

import jreact.Hooks;

import java.util.Map;

/**
 * Hooks of the router. They are static, so use them with a static import and call them while rendering, like
 * {@code useState}:
 * <pre>{@code
 * import static jreact.router.RouterHooks.*;
 *
 * Map<String, String> params = useParams();     // /posts/:id  ->  params.get("id")
 * Navigator go = useNavigate();                  // go.navigate("/posts/7")
 * }</pre>
 */
public final class RouterHooks {
    private RouterHooks() {}

    static RouterContext.RouterState router(String what) {
        RouterContext.RouterState r = Hooks.current().useContext(RouterContext.ROUTER);
        if (r == null) throw new IllegalStateException(what + " must be inside a <Router> (BrowserRouter, HashRouter)");
        return r;
    }

    /** The current location (pathname, search, hash), without the router's basename. Re-renders on every navigation. */
    public static Location useLocation() { return router("useLocation()").location(); }

    /** The params of the route whose element is rendering, including its parents' ({@code /users/:id} gives {@code id}), or an empty map. */
    public static Map<String, String> useParams() { return Hooks.current().useContext(RouterContext.ROUTE).params(); }

    /** Moves around the app. Relative targets ({@code edit}, {@code ../list}) resolve against the current route. The same object across renders. */
    public static Navigator useNavigate() {
        RouterContext.RouterState r = router("useNavigate()");
        String base = Hooks.current().useContext(RouterContext.ROUTE).base();
        return Hooks.current().useMemo(() -> new RouterNavigator(r.history(), r.basename(), base),
                new Object[]{r.history(), r.basename(), base});
    }

    /** The query string and a way to change it. */
    public static SearchState useSearchParams() {
        RouterContext.RouterState r = router("useSearchParams()");
        Location loc = r.location();
        SearchParams params = Hooks.current().useMemo(() -> SearchParams.parse(loc.search()), new Object[]{loc.search()});
        return new SearchState(params, useNavigate(), loc.pathname());
    }
}

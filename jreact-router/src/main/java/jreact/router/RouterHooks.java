package jreact.router;

import jreact.Hooks;
import jreact.Ref;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
        return Hooks.current().useMemo(() -> new RouterNavigator(r.history(), r.basename(), base, r.data()),
                new Object[]{r.history(), r.basename(), base, r.data()});
    }

    /** The query string and a way to change it. */
    public static SearchState useSearchParams() {
        RouterContext.RouterState r = router("useSearchParams()");
        Location loc = r.location();
        SearchParams params = Hooks.current().useMemo(() -> SearchParams.parse(loc.search()), new Object[]{loc.search()});
        return new SearchState(params, useNavigate(), loc.pathname());
    }

    // ================================================================ matching

    /** The match of {@code pattern} ({@code /posts/:id}) against the current location, or null. The location must start with the pattern ({@code end} is true): {@code /posts/:id} does not match {@code /posts/7/edit}. */
    public static RouteMatch useMatch(String pattern) {
        Location loc = router("useMatch()").location();
        List<RouteMatch> m = RouteMatcher.match(List.of(RouteObject.of(pattern)), loc.pathname());
        return m == null ? null : m.get(m.size() - 1);
    }

    /** The value the parent route's {@code <Outlet context={...}/>} gave, or null. */
    public static Object useOutletContext() { return Hooks.current().useContext(RouterContext.OUTLET); }

    // ================================================================ data router

    private static RouterContext.DataState data(String what) {
        RouterContext.DataState d = Hooks.current().useContext(RouterContext.DATA);
        if (d == null) throw new IllegalStateException(what + " only works under a <RouterProvider> (data router: createBrowserRouter, createHashRouter, createMemoryRouter)");
        return d;
    }

    private static RouteObject currentRoute(String what) {
        RouteObject r = Hooks.current().useContext(RouterContext.ROUTE).route();
        if (r == null) throw new IllegalStateException(what + " must be called inside a route's element");
        return r;
    }

    /** What the loader of the current route returned. */
    public static Object useLoaderData() {
        RouteObject r = currentRoute("useLoaderData()");
        return data("useLoaderData()").snapshot().loaderData().get(r.id());
    }

    /** What the loader of the route with {@code routeId} returned (a parent's data from a child), or null. */
    public static Object useRouteLoaderData(String routeId) { return data("useRouteLoaderData()").snapshot().loaderData().get(routeId); }

    /** What the action of the current route returned after the last submission, or null. */
    public static Object useActionData() {
        RouteObject r = currentRoute("useActionData()");
        return data("useActionData()").snapshot().actionData().get(r.id());
    }

    /** In an {@code errorElement}: what was thrown ({@link ErrorResponse} for "not found" and your own status errors, else the exception). */
    public static Object useRouteError() {
        RouterContext.RouteState rs = Hooks.current().useContext(RouterContext.ROUTE);
        return rs.index() == rs.matches().size() - 1 ? rs.error() : null;
    }

    /** Is a navigation going on? {@code state()} is IDLE, LOADING (loaders running) or SUBMITTING (action running); {@code location()} is where it goes. */
    public static DataRouter.Navigation useNavigation() { return data("useNavigation()").snapshot().navigation(); }

    /** How the current page was reached: POP (back/forward, first load), PUSH or REPLACE. */
    public static DataRouter.HistoryAction useNavigationType() { return data("useNavigationType()").snapshot().historyAction(); }

    /** The matched routes (outermost first) with their data and {@code handle}: breadcrumbs, page titles. */
    public record MatchInfo(String id, String pathname, Map<String, String> params, Object data, Object handle) {}

    public static List<MatchInfo> useMatches() {
        RouterContext.DataState d = data("useMatches()");
        List<RouteMatch> ms = Hooks.current().useContext(RouterContext.ROUTE).matches();
        List<MatchInfo> out = new ArrayList<>();
        for (RouteMatch m : ms) {
            out.add(new MatchInfo(m.route().id(), m.pathname(), m.params(), d.snapshot().loaderData().get(m.route().id()), m.route().handle()));
        }
        return out;
    }

    /** Re-runs all loaders: {@code revalidate()}, and {@code state()} is true while it runs. */
    public record Revalidator(boolean busy, Runnable revalidate) {}

    public static Revalidator useRevalidator() {
        RouterContext.DataState d = data("useRevalidator()");
        return new Revalidator(d.snapshot().revalidating(), d.router()::revalidate);
    }

    /** Sends data to an action from code (the same thing {@code <Form>} does). The same object across renders. */
    public static Submitter useSubmit() {
        RouterContext.DataState d = data("useSubmit()");
        RouterContext.RouteState route = Hooks.current().useContext(RouterContext.ROUTE);
        String base = route.base();
        return Hooks.current().useMemo(() -> (Submitter) (fields, options) -> {
            Submitter.Options o = options == null ? Submitter.Options.DEFAULT : options;
            String pathname = d.router().state().location().pathname();
            String target = o.action() == null ? defaultAction(route, d.router().state().location())
                    : Paths.resolve(base, pathname, o.action());
            d.router().submit(target, o.method() == null ? "POST" : o.method(), fields, o.replace());
        }, new Object[]{d.router(), base});
    }

    /** Where a form without {@code action} goes: the current route (an index route is marked {@code ?index}), keeping the query of a non-index route. */
    static String defaultAction(RouterContext.RouteState route, Location current) {
        if (route.route() != null && route.route().index()) return route.base() + "?index";
        return route.base() + current.search();
    }

    private static int fetcherCounter;

    /** A fetcher: load or submit without navigating. Gives up what it runs when the component goes away. */
    public static Fetcher useFetcher() {
        RouterContext.DataState d = data("useFetcher()");
        String base = Hooks.current().useContext(RouterContext.ROUTE).base();
        Ref<String> key = Hooks.current().useRef(null);
        if (key.current == null) key.current = "fetcher-" + (++fetcherCounter);
        String k = key.current;
        Hooks.current().useEffect(() -> () -> d.router().deleteFetcher(k), new Object[]{d.router(), k});
        return new Fetcher(d.router(), k, d.snapshot().fetchers().get(k), base, d.snapshot().location().pathname());
    }

    /** All fetchers that exist right now (a global "saving..." indicator). */
    public static List<DataRouter.FetcherState> useFetchers() { return new ArrayList<>(data("useFetchers()").snapshot().fetchers().values()); }

    /**
     * Holds back navigations while {@code shouldBlock} is true ("you have unsaved changes"). The returned blocker is
     * {@code blocked()} when one was stopped: show a dialog, then {@code proceed()} or {@code reset()}. Only navigations that start from
     * the app ({@code Link}, {@code navigate}, forms) are held; the browser's back button cannot be.
     */
    public static DataRouter.Blocker useBlocker(Predicate<DataRouter.BlockerArgs> shouldBlock) {
        RouterContext.DataState d = data("useBlocker()");
        Ref<Predicate<DataRouter.BlockerArgs>> latest = Hooks.current().useRef(shouldBlock);
        latest.current = shouldBlock;
        Ref<String> key = Hooks.current().useRef(null);
        if (key.current == null) key.current = "blocker-" + (++fetcherCounter);
        String k = key.current;
        Hooks.current().useEffect(() -> {
            d.router().registerBlocker(k, args -> latest.current.test(args));
            return () -> d.router().unregisterBlocker(k);
        }, new Object[]{d.router(), k});
        return d.snapshot().blockers().getOrDefault(k, DataRouter.Blocker.UNBLOCKED);
    }

    public static DataRouter.Blocker useBlocker(boolean shouldBlock) { return useBlocker(args -> shouldBlock); }
}

package jreact.router;

import jreact.Context;

import java.util.List;
import java.util.Map;

/** The contexts the router is built on. Normally you use {@link RouterHooks} instead of reading them. */
public final class RouterContext {
    private RouterContext() {}

    /**
     * Set by {@link Router} / {@link RouterProvider}: the location (without the basename), where it is kept, and the basename.
     * {@code data} is the {@link DataRouter} under a {@code RouterProvider}, null under a plain {@code Router}.
     */
    public record RouterState(Location location, History history, String basename, DataRouter data) {
        public RouterState(Location location, History history, String basename) { this(location, history, basename, null); }
    }

    /**
     * Set around the element of every matched route: the whole chain of matches and the index of the route
     * whose element is rendering. {@code <Outlet/>} renders the next one. {@code error}: the error shown by the
     * <em>last</em> match of the chain (its errorElement renders instead of its element), null normally.
     */
    public record RouteState(List<RouteMatch> matches, int index, Object error) {
        public RouteState(List<RouteMatch> matches, int index) { this(matches, index, null); }

        /** The path child routes and relative links resolve against. */
        public String base() { return index < 0 ? "/" : matches.get(index).pathnameBase(); }

        public Map<String, String> params() { return index < 0 ? Map.of() : matches.get(index).params(); }

        /** The route whose element is rendering, or null outside of routes. */
        public RouteObject route() { return index < 0 ? null : matches.get(index).route(); }
    }

    /** Set by {@link RouterProvider}: the data router and its latest state (the page re-renders when it changes). */
    public record DataState(DataRouter router, DataRouter.Snapshot snapshot) {}

    /** The value of {@code <Outlet context={...}/>}, for {@code useOutletContext()}. */
    public static final Context<Object> OUTLET = Context.create(null, "outlet-context");

    public static final Context<RouterState> ROUTER = Context.create(null, "router");

    public static final Context<DataState> DATA = Context.create(null, "data-router");

    public static final Context<RouteState> ROUTE = Context.create(new RouteState(List.of(), -1), "route");
}

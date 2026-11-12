package jreact.router;

import jreact.Context;

import java.util.List;
import java.util.Map;

/** The two contexts the router is built on. Normally you use {@link RouterHooks} instead of reading them. */
public final class RouterContext {
    private RouterContext() {}

    /** Set by {@link Router}: the location (without the basename), where it is kept, and the basename. */
    public record RouterState(Location location, History history, String basename) {}

    /**
     * Set around the element of every matched route: the whole chain of matches and the index of the route
     * whose element is rendering. {@code <Outlet/>} renders the next one.
     */
    public record RouteState(List<RouteMatch> matches, int index) {
        /** The path child routes and relative links resolve against. */
        public String base() { return index < 0 ? "/" : matches.get(index).pathnameBase(); }

        public Map<String, String> params() { return index < 0 ? Map.of() : matches.get(index).params(); }
    }

    public static final Context<RouterState> ROUTER = Context.create(null, "router");

    public static final Context<RouteState> ROUTE = Context.create(new RouteState(List.of(), -1), "route");
}

package jreact.router;

import java.util.ArrayList;
import java.util.List;

/** Ways to make a {@link DataRouter}. In the browser use {@code Routers.createBrowserRouter} / {@code createHashRouter} from jreact-web. */
public final class Routers {
    private Routers() {}

    /** A router on an in-memory history: tests and rendering on the JVM. {@code initialEntries}: the first URL (default {@code /}). */
    public static DataRouter createMemoryRouter(List<RouteObject> routes, String initialEntry) {
        return new DataRouter(routes, new MemoryHistory(initialEntry == null ? "/" : initialEntry), null);
    }

    public static DataRouter createMemoryRouter(List<RouteObject> routes) { return createMemoryRouter(routes, "/"); }

    /** {@code <Route>} elements to route objects, for people who prefer JSX: {@code createRoutesFromElements(<Route path="/" ...>...</Route>)}. */
    public static List<RouteObject> createRoutesFromElements(Object... elements) {
        return RouteObjects.fromChildren(new ArrayList<>(List.of(elements)));
    }
}

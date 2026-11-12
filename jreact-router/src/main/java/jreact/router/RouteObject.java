package jreact.router;

import jreact.Element;

import java.util.List;

/**
 * A route as data (what a {@code <Route>} element says, or what you pass to {@code createBrowserRouter}).
 * {@code path} is relative to the parent route (or absolute, starting with {@code /}, but then it must lie under the
 * parent's path); null means "no path of its own" (a layout route). An {@code index} route renders at the path of its parent.
 * <p>
 * The other fields make it a <em>data</em> route (see {@link DataRouter}): {@code loader} fetches data before the route
 * renders, {@code action} handles form submissions, {@code errorElement} replaces the route's element when it, a child
 * or one of their loaders/actions fails, {@code shouldRevalidate} limits reloading, {@code handle} is anything you want to read
 * back with {@code useMatches()} (breadcrumbs, page titles), {@code id} names the route for {@code useRouteLoaderData}
 * (generated when null).
 * <pre>{@code
 * RouteObject.of("/").element(<Root />).errorElement(<RootError />).children(
 *     RouteObject.indexRoute().element(<Home />),
 *     RouteObject.of("posts/:id").loader(a -> Posts.find(a.params().get("id"))).element(<Post />));
 * }</pre>
 */
public record RouteObject(String id, String path, boolean index, Element element, Element errorElement, Loader loader,
                          Action action, ShouldRevalidate shouldRevalidate, Object handle, Lazy lazy, List<RouteObject> children) {

    public RouteObject {
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** A plain route (what v1 had). */
    public RouteObject(String path, boolean index, Element element, List<RouteObject> children) {
        this(null, path, index, element, null, null, null, null, null, null, children);
    }

    /** A route at {@code path}. */
    public static RouteObject of(String path) { return new RouteObject(path, false, null, List.of()); }

    /** A route without a path: only wraps its children (layout, error boundary). */
    public static RouteObject layout() { return new RouteObject(null, false, null, List.of()); }

    /** An index route: renders at the path of its parent. */
    public static RouteObject indexRoute() { return new RouteObject(null, true, null, List.of()); }

    public RouteObject id(String v) { return new RouteObject(v, path, index, element, errorElement, loader, action, shouldRevalidate, handle, lazy, children); }

    public RouteObject element(Element v) { return new RouteObject(id, path, index, v, errorElement, loader, action, shouldRevalidate, handle, lazy, children); }

    public RouteObject errorElement(Element v) { return new RouteObject(id, path, index, element, v, loader, action, shouldRevalidate, handle, lazy, children); }

    public RouteObject loader(Loader v) { return new RouteObject(id, path, index, element, errorElement, v, action, shouldRevalidate, handle, lazy, children); }

    public RouteObject action(Action v) { return new RouteObject(id, path, index, element, errorElement, loader, v, shouldRevalidate, handle, lazy, children); }

    public RouteObject shouldRevalidate(ShouldRevalidate v) { return new RouteObject(id, path, index, element, errorElement, loader, action, v, handle, lazy, children); }

    public RouteObject handle(Object v) { return new RouteObject(id, path, index, element, errorElement, loader, action, shouldRevalidate, v, lazy, children); }

    /** See {@link Lazy}: the route's parts are produced when it is first matched. */
    public RouteObject lazy(Lazy v) { return new RouteObject(id, path, index, element, errorElement, loader, action, shouldRevalidate, handle, v, children); }

    public RouteObject children(RouteObject... v) { return children(List.of(v)); }

    public RouteObject children(List<RouteObject> v) { return new RouteObject(id, path, index, element, errorElement, loader, action, shouldRevalidate, handle, lazy, v); }
}

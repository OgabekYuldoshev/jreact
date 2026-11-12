package jreact.router;

/**
 * Supplies the parts of a route (element, loader, action, errorElement, shouldRevalidate, handle) the first time the route
 * is matched, instead of when the router is created. The navigation shows {@code LOADING} until it is done, and the result is
 * kept: it runs once per route. Properties the route already has win; {@code path}, {@code index}, {@code id} and
 * {@code children} cannot come from here (the router must know the shape of the tree to match URLs).
 * <pre>{@code
 * RouteObject.of("reports").lazy(() -> Promise.resolved(Reports.route()))      // Reports.route(): RouteObject.layout().element(..).loader(..)
 * RouteObject.of("reports").lazy(() -> fetchSomething().then(json -> RouteObject.layout().element(..)))   // asynchronously
 * }</pre>
 * <b>Does not shrink the bundle</b>: a TeaVM program is one {@code app.js}, so the code of a lazy route is downloaded
 * with the rest. What you get is deferred work: building heavy route parts, fetching them, or reading a config only when needed.
 */
@FunctionalInterface
public interface Lazy {
    Promise<RouteObject> load() throws Exception;
}

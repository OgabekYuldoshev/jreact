package jreact.router;

import java.util.Map;

/**
 * Decides whether a route's loader runs again after a navigation, action or {@code revalidate()}. By default it runs
 * when the route is new, its params or the query string changed, or after any action. Return {@code false} to keep the old
 * data (expensive loader of a layout), or {@code true} to refresh more often.
 */
@FunctionalInterface
public interface ShouldRevalidate {

    record Args(Location currentUrl, Location nextUrl, Map<String, String> currentParams, Map<String, String> nextParams,
                String formMethod, Object actionResult, boolean defaultShouldRevalidate) {}

    boolean shouldRevalidate(Args args);
}

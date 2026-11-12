package jreact.router;

import java.util.Map;

/**
 * One route of a matched chain (outermost first). {@code pathname} is what this route matched, {@code pathnameBase}
 * the part that child routes and relative links resolve against (the same, but without what a {@code *} swallowed).
 * {@code params} holds the params of this route and all its parents.
 */
public record RouteMatch(RouteObject route, Map<String, String> params, String pathname, String pathnameBase) {}

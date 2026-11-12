package jreact.router;

import jreact.Element;

import java.util.List;

/**
 * A route as data: what a {@code <Route>} element says. {@code path} is relative to the parent route
 * (or absolute, starting with {@code /}, but then it must lie under the parent's path); null means "no path of its own"
 * (a layout route). An {@code index} route renders at the path of its parent.
 */
public record RouteObject(String path, boolean index, Element element, List<RouteObject> children) {}

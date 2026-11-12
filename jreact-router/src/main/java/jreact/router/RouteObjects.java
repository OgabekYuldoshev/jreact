package jreact.router;

import jreact.Element;

import java.util.ArrayList;
import java.util.List;

/** Reads {@code <Route>} elements into {@link RouteObject}s. */
final class RouteObjects {
    private RouteObjects() {}

    static List<RouteObject> fromChildren(List<Object> children) {
        List<RouteObject> out = new ArrayList<>();
        add(children, out);
        return out;
    }

    private static void add(Object node, List<RouteObject> out) {
        if (node == null || node instanceof Boolean) return;
        if (node instanceof Iterable<?> list) { for (Object o : list) add(o, out); return; }
        if (node instanceof Element e) {
            if (e.kind() == Element.Kind.FRAGMENT) { add(e.children(), out); return; }
            if (e.kind() == Element.Kind.COMPONENT && e.type() == Route.class) {
                Route.Props p = (Route.Props) e.props();
                out.add(new RouteObject(p.id(), p.path(), p.index(), p.element(), p.errorElement(), p.loader(), p.action(), p.shouldRevalidate(), p.handle(), p.lazy(),
                        fromChildren(p.children() == null ? List.of() : p.children())));
                return;
            }
        }
        throw new IllegalArgumentException("the children of <Routes> and <Route> must be <Route> elements, but found " + node);
    }
}

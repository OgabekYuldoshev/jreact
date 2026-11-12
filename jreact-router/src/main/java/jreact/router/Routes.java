package jreact.router;

import jreact.Component;
import jreact.Element;
import jreact.router.RouterContext.RouteState;
import jreact.router.RouterContext.RouterState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the {@code <Route>} that matches the location and renders its element (the best match, not the first:
 * see {@link RouteMatcher}). Renders nothing when no route matches: add {@code <Route path="*" .../>} for a 404 page.
 * A {@code <Routes>} inside the element of a route with a {@code path="users/*"} matches the rest of the path.
 */
public class Routes extends Component<Routes.Props> {

    public record Props(List<Object> children) {}

    @Override
    public Element render(Props props) {
        RouterState router = RouterHooks.router("<Routes>");
        RouteState parent = useContext(RouterContext.ROUTE);
        String parentBase = parent.base();
        String pathname = router.location().pathname();
        if (!Paths.startsWith(pathname, parentBase)) return null;

        List<RouteMatch> local = RouteMatcher.match(RouteObjects.fromChildren(props.children()), Paths.strip(pathname, parentBase));
        if (local == null) return null;

        // one chain: the matches of the routes above this <Routes>, then ours with absolute paths and the parents' params
        List<RouteMatch> all = new ArrayList<>(parent.index() < 0 ? List.of() : parent.matches().subList(0, parent.index() + 1));
        int first = all.size();
        for (RouteMatch m : local) {
            Map<String, String> params = new LinkedHashMap<>(parent.params());
            params.putAll(m.params());
            all.add(new RouteMatch(m.route(), Map.copyOf(params), Paths.join(parentBase, m.pathname()), Paths.join(parentBase, m.pathnameBase())));
        }
        return renderMatch(all, first);
    }

    /** The element of {@code matches[index]}, with the route context set (an element-less route renders its child via {@code <Outlet/>}). */
    static Element renderMatch(List<RouteMatch> matches, int index) { return renderMatch(matches, index, null); }

    /** Same; when {@code error} is not null and this is the last match, the route's errorElement is shown instead of its element. */
    static Element renderMatch(List<RouteMatch> matches, int index, Object error) {
        RouteObject route = matches.get(index).route();
        Element element;
        if (error != null && index == matches.size() - 1) element = route.errorElement() != null ? route.errorElement() : DefaultError.element();
        else element = route.element();
        if (element == null) element = jreact.Jsx.c(Outlet.class, Outlet::new, null, new Outlet.Props(null));
        return RouterContext.ROUTE.provide(new RouteState(matches, index, error), element);
    }
}

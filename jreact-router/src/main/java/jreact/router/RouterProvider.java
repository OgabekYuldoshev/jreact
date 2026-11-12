package jreact.router;

import jreact.Component;
import jreact.Element;
import jreact.State;
import jreact.router.RouterContext.DataState;
import jreact.router.RouterContext.RouterState;

import java.util.List;

/**
 * Renders a {@link DataRouter}: the matched routes with their loader data, or an {@code errorElement} where something failed.
 * Until the first page's loaders are done {@code fallbackElement} shows (nothing when null).
 * <pre>{@code
 * DataRouter router = createBrowserRouter(routes);
 * JReactWeb.createRoot("root").render(<RouterProvider router={router} />);
 * }</pre>
 */
public class RouterProvider extends Component<RouterProvider.Props> {

    public record Props(DataRouter router, Element fallbackElement) {}

    @Override
    public Element render(Props props) {
        DataRouter router = props.router();
        State<DataRouter.Snapshot> shown = useState(router.state());
        useEffect(() -> {
            Runnable stop = router.subscribe(shown::set);
            shown.set(router.state());       // something changed between render and subscribe
            return stop;
        }, router);

        DataRouter.Snapshot s = router.state();      // the router is the source of truth; the state only triggers renders
        RouterState rs = new RouterState(s.location(), router.history(), router.basename(), router);
        Element content = body(s, props);
        return RouterContext.ROUTER.provide(rs, RouterContext.DATA.provide(new DataState(router, s), content));
    }

    private static Element body(DataRouter.Snapshot s, Props props) {
        if (!s.initialized() || s.matches() == null) return props.fallbackElement();
        List<RouteMatch> matches = s.matches();
        // an error shows at the route that owns it; what is below that route is not rendered
        for (int i = 0; i < matches.size(); i++) {
            Object error = s.errors().get(matches.get(i).route().id());
            if (error != null) return Routes.renderMatch(matches.subList(0, i + 1), 0, error);
        }
        return Routes.renderMatch(matches, 0);
    }
}

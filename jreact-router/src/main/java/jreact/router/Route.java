package jreact.router;

import jreact.Component;
import jreact.Element;

import java.util.List;

/**
 * Describes one route inside {@code <Routes>}; it renders nothing by itself.
 * <pre>{@code
 * <Routes>
 *     <Route path="/" element={<Layout />}>
 *         <Route index element={<Home />} />
 *         <Route path="posts/:id" element={<Post />} />
 *         <Route path="*" element={<NotFound />} />
 *     </Route>
 * </Routes>
 * }</pre>
 * {@code path}: static segments, {@code :param}, a final {@code *}. A route without a path is a layout route
 * (its {@code element} wraps the children; put an {@code <Outlet/>} where they go). {@code index} renders at the
 * parent's path. Without an {@code element} the route just renders its children.
 */
public class Route extends Component<Route.Props> {

    /**
     * {@code id}, {@code loader}, {@code action}, {@code errorElement}, {@code shouldRevalidate} and {@code handle} only do something
     * under a {@code RouterProvider} (data router), see {@link RouteObject}.
     */
    public record Props(String path, boolean index, Element element, String id, Element errorElement, Loader loader, Action action,
                        ShouldRevalidate shouldRevalidate, Object handle, List<Object> children) {}

    @Override public Element render(Props props) { return null; }
}

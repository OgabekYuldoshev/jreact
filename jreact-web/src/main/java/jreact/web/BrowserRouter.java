package jreact.web;

import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.Ref;
import jreact.router.History;
import jreact.router.Router;

import java.util.List;

/**
 * {@code <BrowserRouter>} routes on real paths ({@code /posts/7}), see {@link BrowserHistory}.
 * {@code basename}: a fixed prefix the app is served under, e.g. {@code /docs}; leave it null for the site root.
 */
public class BrowserRouter extends Component<BrowserRouter.Props> {

    public record Props(String basename, List<Object> children) {}

    @Override
    public Element render(Props props) {
        Ref<History> history = useRef(null);
        if (history.current == null) history.current = new BrowserHistory();
        return Jsx.c(Router.class, Router::new, null, new Router.Props(history.current, props.basename(), props.children()));
    }
}

package jreact.web;

import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.Ref;
import jreact.router.History;
import jreact.router.Router;

import java.util.List;

/** {@code <HashRouter>} routes on the part after the {@code #} ({@code index.html#/posts/7}): works on any static host, see {@link HashHistory}. */
public class HashRouter extends Component<HashRouter.Props> {

    public record Props(String basename, List<Object> children) {}

    @Override
    public Element render(Props props) {
        Ref<History> history = useRef(null);
        if (history.current == null) history.current = new HashHistory();
        return Jsx.c(Router.class, Router::new, null, new Router.Props(history.current, props.basename(), props.children()));
    }
}

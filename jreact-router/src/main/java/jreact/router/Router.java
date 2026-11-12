package jreact.router;

import jreact.Component;
import jreact.Element;
import jreact.State;
import jreact.router.RouterContext.RouterState;

import java.util.List;

/**
 * The root of routing: keeps the location of a {@link History} and shares it with everything inside. In the browser
 * use {@code BrowserRouter} or {@code HashRouter} (jreact-web), they create the history for you.
 * {@code basename} is a fixed prefix the app is served under (for example {@code /docs}); routes and links never see it.
 */
public class Router extends Component<Router.Props> {

    public record Props(History history, String basename, List<Object> children) {}

    @Override
    public Element render(Props props) {
        History history = props.history();
        String basename = props.basename() == null || props.basename().isEmpty() || props.basename().equals("/")
                ? "" : Paths.normalize(props.basename());

        // The history is the source of truth: it is read while rendering. The state only makes a navigation re-render us.
        Location current = history.location();
        State<Location> trigger = useState(current);
        useEffect(() -> {
            Runnable stop = history.listen(() -> trigger.set(history.location()));
            trigger.set(history.location());       // a navigation between render and now (e.g. <Navigate> in a child effect)
            return stop;
        }, history);

        Location location = current;
        if (!basename.isEmpty()) {
            if (!Paths.startsWith(current.pathname(), basename)) return null;
            location = new Location(Paths.strip(current.pathname(), basename), current.search(), current.hash());
        }
        return RouterContext.ROUTER.provide(new RouterState(location, history, basename), props.children());
    }
}

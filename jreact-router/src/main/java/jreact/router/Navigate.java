package jreact.router;

import jreact.Component;
import jreact.Element;

/** Redirects when it renders: {@code <Navigate to="/login" replace />}. It renders nothing. Use {@code replace} so back does not return to the redirecting page. */
public class Navigate extends Component<Navigate.Props> {

    public record Props(String to, boolean replace) {}

    @Override
    public Element render(Props p) {
        Navigator nav = RouterHooks.useNavigate();
        useEffect(() -> nav.navigate(p.to(), p.replace()), p.to(), p.replace(), nav);
        return null;
    }
}

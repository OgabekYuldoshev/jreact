package jreact.router;

import jreact.Component;
import jreact.Element;
import jreact.router.RouterContext.RouteState;

/** Where a parent route renders its matched child route: put it in the layout's {@code element}. */
public class Outlet extends Component<Outlet.Props> {

    public record Props() {}

    @Override
    public Element render(Props props) {
        RouteState rs = useContext(RouterContext.ROUTE);
        if (rs.index() + 1 >= rs.matches().size()) return null;
        return Routes.renderMatch(rs.matches(), rs.index() + 1);
    }
}

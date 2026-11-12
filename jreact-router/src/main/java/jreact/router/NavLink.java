package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.router.RouterContext.RouterState;

import java.util.List;

/**
 * A {@link Link} that knows whether it points at the current page: then {@code activeClassName} is added to the class
 * and {@code aria-current="page"} is set. It is active for its path and everything below it; with {@code end} only for
 * the exact path. A link to {@code /} is always an {@code end} link (otherwise it would be active everywhere).
 */
public class NavLink extends Component<NavLink.Props> {

    public record Props(String to, boolean end, boolean replace, String className, String activeClassName, String target,
                        String id, List<Object> children) {}

    @Override
    public Element render(Props p) {
        RouterState router = RouterHooks.router("<NavLink>");
        String base = useContext(RouterContext.ROUTE).base();
        Navigator nav = RouterHooks.useNavigate();
        String path = Paths.resolve(base, router.location().pathname(), p.to());
        String onlyPath = path;
        int cut = onlyPath.length();
        if (onlyPath.indexOf('?') >= 0) cut = onlyPath.indexOf('?');
        if (onlyPath.indexOf('#') >= 0) cut = Math.min(cut, onlyPath.indexOf('#'));
        onlyPath = Paths.normalize(onlyPath.substring(0, cut));

        String current = router.location().pathname();
        boolean end = p.end() || onlyPath.equals("/");
        boolean active = end ? Paths.normalize(current).equalsIgnoreCase(onlyPath) : Paths.startsWith(current, onlyPath);

        String className = p.className() == null ? "" : p.className();
        if (active && p.activeClassName() != null) className = (className + " " + p.activeClassName()).trim();
        Attrs attrs = new Attrs()
                .attr("href", router.history().href(router.basename() + path))
                .attr("id", p.id())
                .attr("className", className.isEmpty() ? null : className)
                .attr("target", p.target())
                .attr("aria-current", active ? "page" : null)
                .on("onClick", e -> {
                    if (e.modified() || (p.target() != null && !p.target().equals("_self"))) return;
                    e.preventDefault();
                    nav.navigate(p.to(), p.replace());
                });
        return Jsx.el("a", null, attrs, p.children() == null ? new Object[0] : p.children().toArray());
    }
}

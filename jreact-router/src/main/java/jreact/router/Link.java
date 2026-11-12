package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.router.RouterContext.RouterState;

import java.util.List;

/**
 * An {@code <a>} that navigates without reloading the page. It has a real {@code href}, so open-in-new-tab,
 * middle click and copy-link work; plain left clicks are handled here, modified clicks and {@code target="_blank"}
 * are left to the browser. {@code to} follows {@link Navigator#navigate}.
 */
public class Link extends Component<Link.Props> {

    public record Props(String to, boolean replace, String className, String target, String title, String id,
                        List<Object> children) {}

    @Override
    public Element render(Props p) {
        RouterState router = RouterHooks.router("<Link>");
        String base = useContext(RouterContext.ROUTE).base();
        Navigator nav = RouterHooks.useNavigate();
        String path = Paths.resolve(base, router.location().pathname(), p.to());
        Attrs attrs = new Attrs()
                .attr("href", router.history().href(router.basename() + path))
                .attr("id", p.id()).attr("className", p.className()).attr("target", p.target()).attr("title", p.title())
                .on("onClick", e -> {
                    if (e.modified() || (p.target() != null && !p.target().equals("_self"))) return;
                    e.preventDefault();
                    nav.navigate(p.to(), p.replace());
                });
        return Jsx.el("a", null, attrs, p.children() == null ? new Object[0] : p.children().toArray());
    }
}

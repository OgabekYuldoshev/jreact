package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;

/** What a route shows when it fails and has no {@code errorElement}. */
public class DefaultError extends Component<DefaultError.Props> {

    public record Props() {}

    static Element element() { return Jsx.c(DefaultError.class, DefaultError::new, null, new Props()); }

    @Override
    public Element render(Props props) {
        Object error = RouterHooks.useRouteError();
        String title, detail;
        if (error instanceof ErrorResponse r) { title = r.status() + " " + r.statusText(); detail = r.data() == null ? "" : String.valueOf(r.data()); }
        else { title = "Unexpected Application Error!"; detail = error instanceof Throwable t ? String.valueOf(t.getMessage()) : String.valueOf(error); }
        return Jsx.el("div", null, new Attrs().attr("role", "alert").attr("data-router-error", "true"),
                Jsx.el("h2", null, null, title), Jsx.el("pre", null, null, detail));
    }
}

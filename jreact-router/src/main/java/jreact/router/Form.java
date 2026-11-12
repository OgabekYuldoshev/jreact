package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;

import java.util.List;

/**
 * A {@code <form>} that does not reload the page: it submits through the router. {@code method="post"} (also put/patch/delete)
 * runs the action of the route and then reloads the loaders; {@code method="get"} (the default) navigates to {@code action}
 * with the fields as the query, so a search form shows up in the URL. {@code action} defaults to the current route and resolves
 * like {@code <Link to>}. With {@code fetcher} the submission does not navigate (see {@code useFetcher}).
 * <pre>{@code
 * <Form method="post"><input name="title" /><button>Save</button></Form>
 * }</pre>
 */
public class Form extends Component<Form.Props> {

    public record Props(String method, String action, boolean replace, Fetcher fetcher, String id, String className, List<Object> children) {}

    @Override
    public Element render(Props p) {
        RouterContext.RouterState router = RouterHooks.router("<Form>");
        Submitter submitter = RouterHooks.useSubmit();
        String method = p.method() == null ? "get" : p.method().toLowerCase();
        Attrs attrs = new Attrs().attr("method", method.equals("get") ? "get" : "post").attr("id", p.id()).attr("className", p.className())
                .on("onSubmit", e -> {
                    e.preventDefault();
                    String fields = e.formData();
                    if (p.fetcher() != null) p.fetcher().submit(fields, p.action(), method.equals("get") ? "GET" : method);
                    else submitter.submit(fields, new Submitter.Options(method, p.action(), p.replace()));
                });
        return Jsx.el("form", null, attrs, p.children() == null ? new Object[0] : p.children().toArray());
    }
}

package dev.jreact.spring.sample;

import dev.jreact.api.ClientApp;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.router.ErrorResponse;
import jreact.router.Promise;
import jreact.router.Redirect;
import jreact.router.RouteObject;
import jreact.router.RouterHooks;

import java.util.ArrayList;
import java.util.List;

/** The routes the SSR test renders on the server, like a client module's App. */
public class SampleApp implements ClientApp {

    public static class Page extends Component<Page.Props> {
        public record Props() {}

        @Override
        @SuppressWarnings("unchecked")
        public Element render(Props p) {
            List<Greeting> all = (List<Greeting>) RouterHooks.useLoaderData();
            useHead(jreact.Head.title("Greetings (" + all.size() + ")").meta("description", "say \"hi\" <b>"));
            List<Object> items = new ArrayList<>();
            for (Greeting g : all) items.add(Jsx.el("li", g.text(), null, g.text(), " x", g.times()));
            return Jsx.el("main", null, null, Jsx.el("h1", null, null, "<Greetings> & more"), Jsx.el("ul", null, null, items));
        }
    }

    public static class Oops extends Component<Oops.Props> {
        public record Props() {}
        @Override public Element render(Props p) { return Jsx.el("p", null, null, "oops"); }
    }

    @Override
    public List<RouteObject> routes() {
        return List.of(
                RouteObject.of("/").id("home").element(Jsx.c(Page.class, Page::new, null, new Page.Props()))
                        .loader(a -> GreetApiClient.all()),
                RouteObject.of("/old").id("old").loader(a -> { throw Redirect.to("/"); }),
                RouteObject.of("/slow").id("slow").element(Jsx.c(Oops.class, Oops::new, null, new Oops.Props()))
                        .loader(a -> Promise.pending()),
                RouteObject.of("/boom").id("boom").element(Jsx.c(Oops.class, Oops::new, null, new Oops.Props()))
                        .loader(a -> { throw new ErrorResponse(418, "I'm a teapot", "short and stout"); }));
    }
}

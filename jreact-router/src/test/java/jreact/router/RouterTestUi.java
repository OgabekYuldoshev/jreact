package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.dom.NativeEvent;

import java.util.ArrayList;
import java.util.List;

import static jreact.router.DataRouterTest.check;
import static jreact.router.DataRouterTest.eq;
import static jreact.router.DataRouterTest.test;
import static jreact.router.RouterHooks.*;

/** RouterProvider, hooks, Link and Form on the fake DOM of RouterTest. */
final class RouterTestUi {
    private RouterTestUi() {}

    record Submit(RouterTest.FNode target, String fields, boolean[] prevented) implements NativeEvent<RouterTest.FNode> {
        public String type() { return "submit"; }
        public String targetTag() { return "form"; }
        public String targetType() { return null; }
        public String value() { return null; }
        public boolean checked() { return false; }
        public void preventDefault() { prevented[0] = true; }
        public String formData() { return fields; }
    }

    static Element el(String tag, Attrs attrs, Object... children) { return Jsx.el(tag, null, attrs, children); }
    static Element c(Class<? extends Component<Void>> unused) { return null; }

    static final class Show extends Component<Show.Props> {
        record Props(String id) {}
        public Element render(Props p) {
            Object d = useLoaderData();
            return el("p", new Attrs().attr("id", p.id()), p.id() + ":" + d);
        }
        static Element make(String id) { return Jsx.c(Show.class, Show::new, null, new Props(id)); }
    }

    static final class Shell extends Component<Shell.Props> {
        record Props() {}
        public Element render(Props p) {
            DataRouter.Navigation nav = useNavigation();
            return el("div", null,
                    el("span", new Attrs().attr("id", "nav"), nav.state().name()),
                    Jsx.c(Link.class, Link::new, null, new Link.Props("/posts/1", false, null, null, null, "to-1", Jsx.children("one"))),
                    Jsx.c(Link.class, Link::new, null, new Link.Props("/posts/slow", false, null, null, null, "to-slow", Jsx.children("slow"))),
                    Jsx.c(Outlet.class, Outlet::new, null, new Outlet.Props(null)));
        }
        static Element make() { return Jsx.c(Shell.class, Shell::new, null, new Props()); }
    }

    static final class Oops extends Component<Oops.Props> {
        record Props() {}
        public Element render(Props p) {
            Object e = useRouteError();
            String what = e instanceof ErrorResponse r ? "status " + r.status() : "error " + ((Throwable) e).getMessage();
            return el("p", new Attrs().attr("id", "oops"), what);
        }
        static Element make() { return Jsx.c(Oops.class, Oops::new, null, new Props()); }
    }

    static final class AddForm extends Component<AddForm.Props> {
        record Props() {}
        public Element render(Props p) {
            Object result = useActionData();
            return el("div", null,
                    Jsx.c(Form.class, Form::new, null, new Form.Props("post", null, false, null, "add", null, Jsx.children(el("button", null, "go")))),
                    el("span", new Attrs().attr("id", "result"), String.valueOf(result)),
                    el("span", new Attrs().attr("id", "items"), String.valueOf(useLoaderData())));
        }
        static Element make() { return Jsx.c(AddForm.class, AddForm::new, null, new Props()); }
    }

    static Element provider(DataRouter router, Element fallback) {
        return Jsx.c(RouterProvider.class, RouterProvider::new, null, new RouterProvider.Props(router, fallback));
    }

    static void run() {
        test("RouterProvider shows the fallback until the first loaders finish, then the page with useLoaderData", () -> {
            List<Promise<Object>> gate = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(
                    RouteObject.of("/").id("root").element(Shell.make()).children(
                            RouteObject.of("posts/:id").id("post").element(Show.make("post")).loader(a -> { Promise<Object> p = Promise.pending(); gate.add(p); return p; }))), "/posts/9");
            RouterTest.Env env = new RouterTest.Env();
            env.render(provider(router, el("p", new Attrs().attr("id", "wait"), "loading...")));
            eq("<p id=\"wait\">loading...</p>", env.html());
            gate.get(0).resolve("nine");
            env.settle();
            eq("post:nine", env.text("post"));
            check(env.html().contains("id=\"nav\""), "layout there");
        });

        test("clicking a Link navigates through the router: the old page stays while LOADING, useNavigation shows it", () -> {
            List<Promise<Object>> gate = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(
                    RouteObject.of("/").id("root").element(Shell.make()).children(
                            RouteObject.indexRoute().id("home").element(el("p", new Attrs().attr("id", "home"), "home")),
                            RouteObject.of("posts/:id").id("post").element(Show.make("post")).loader(a -> {
                                if (a.params().get("id").equals("slow")) { Promise<Object> p = Promise.pending(); gate.add(p); return p; }
                                return "n" + a.params().get("id");
                            }))), "/");
            RouterTest.Env env = new RouterTest.Env();
            env.render(provider(router, null));
            eq("IDLE", env.text("nav"));
            check(!env.click("to-1"), "plain click handled by the router");
            eq("post:n1", env.text("post"));
            check(!env.click("to-slow"), "click");
            eq("LOADING", env.text("nav"));
            eq("post:n1", env.text("post"));               // old page still there
            gate.get(0).resolve("late");
            env.settle();
            eq("post:late", env.text("post"));
            eq("IDLE", env.text("nav"));
        });

        test("errorElement renders with useRouteError: loader error, 404, and the layout around it stays", () -> {
            DataRouter router = Routers.createMemoryRouter(List.of(
                    RouteObject.of("/").id("root").element(Shell.make()).errorElement(Oops.make()).children(
                            RouteObject.indexRoute().id("home").element(el("p", new Attrs().attr("id", "home"), "home")),
                            RouteObject.of("posts/:id").id("post").element(Show.make("post")).loader(a -> { throw new IllegalStateException("kaput"); }))), "/posts/1");
            RouterTest.Env env = new RouterTest.Env();
            env.render(provider(router, null));
            eq("error kaput", env.text("oops"));
            router.navigate("/nope", false);
            env.settle();
            eq("status 404", env.text("oops"));
            router.navigate("/", false);
            env.settle();
            check(env.html().contains("id=\"home\"") && !env.html().contains("oops"), env.html());
        });

        test("without errorElement the default error page shows the problem", () -> {
            DataRouter router = Routers.createMemoryRouter(List.of(RouteObject.of("/").id("root").element(Shell.make()).children(
                    RouteObject.of("x").id("x").element(Show.make("x")).loader(a -> { throw new ErrorResponse(403, "Forbidden", "members only"); }))), "/x");
            RouterTest.Env env = new RouterTest.Env();
            env.render(provider(router, null));
            check(env.html().contains("403 Forbidden") && env.html().contains("members only"), env.html());
        });

        test("<Form method=post> submits through the router: action, then loaders again, actionData readable", () -> {
            List<String> items = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(RouteObject.of("/").id("root").children(
                    RouteObject.of("list").id("list").element(AddForm.make()).loader(a -> List.copyOf(items))
                            .action(a -> { items.add(a.request().form().get("title")); return "added " + a.request().form().get("title"); }))), "/list");
            RouterTest.Env env = new RouterTest.Env();
            env.render(provider(router, null));
            eq("[]", env.text("items"));
            RouterTest.FNode form = env.container.children.get(0).find("add");
            boolean[] prevented = {false};
            env.dom.listeners.get("submit").accept(new Submit(form, "title=milk", prevented));
            env.settle();
            check(prevented[0], "the browser's own submit is stopped");
            eq("added milk", env.text("result"));
            eq("[milk]", env.text("items"));
        });

        test("hooks outside a data router explain themselves", () -> {
            Throwable t = null;
            try {
                RouterTest.Env env = new RouterTest.Env();
                env.render(RouterTest.router(new MemoryHistory("/"), null, RouterTest.routes(RouterTest.route("/", Show.make("x")))));
            } catch (Throwable e) { t = e; }
            check(t != null && String.valueOf(t.getMessage()).contains("RouterProvider"), String.valueOf(t));
        });
    }
}

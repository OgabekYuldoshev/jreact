package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.ServerRenderer;
import jreact.dom.DomOps;
import jreact.dom.DomRoot;
import jreact.dom.NativeEvent;
import jreact.scheduler.ManualSchedulerHost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static jreact.router.RouterHooks.*;

/** Dependency-free tests of jreact-router (run: ./build.sh test). */
public final class RouterTest {

    // ================================================================ element helpers

    static Element route(String path, Element element, Object... children) {
        return Jsx.c(Route.class, Route::new, null, new Route.Props(path, false, element, Jsx.children(children)));
    }
    static Element index(Element element) { return Jsx.c(Route.class, Route::new, null, new Route.Props(null, true, element, Jsx.children())); }
    static Element routes(Object... children) { return Jsx.c(Routes.class, Routes::new, null, new Routes.Props(Jsx.children(children))); }
    static Element outlet() { return Jsx.c(Outlet.class, Outlet::new, null, new Outlet.Props()); }
    static Element router(History h, String basename, Object... children) {
        return Jsx.c(Router.class, Router::new, null, new Router.Props(h, basename, Jsx.children(children)));
    }
    static Element link(String to, String text) {
        return Jsx.c(Link.class, Link::new, null, new Link.Props(to, false, null, null, null, null, Jsx.children(text)));
    }
    static Element navLink(String to, boolean end, String text) {
        return Jsx.c(NavLink.class, NavLink::new, null, new NavLink.Props(to, end, false, "nav", "active", null, null, Jsx.children(text)));
    }
    static Element navigate(String to, boolean replace) { return Jsx.c(Navigate.class, Navigate::new, null, new Navigate.Props(to, replace)); }
    static Element el(String tag, Attrs attrs, Object... children) { return Jsx.el(tag, null, attrs, children); }

    // ================================================================ test components

    /** Shows the params and the location. */
    static class Page extends Component<Page.Props> {
        record Props(String name) {}
        public Element render(Props p) {
            return el("p", new Attrs().attr("id", p.name()), p.name() + " " + useParams() + " " + useLocation());
        }
        static Element make(String name) { return Jsx.c(Page.class, Page::new, null, new Props(name)); }
    }

    /** Layout: a heading and the child route below it. */
    static class Layout extends Component<Layout.Props> {
        record Props(String name) {}
        public Element render(Props p) { return el("div", new Attrs().attr("className", p.name()), el("h1", null, p.name()), outlet()); }
        static Element make(String name) { return Jsx.c(Layout.class, Layout::new, null, new Props(name)); }
    }

    /** Buttons that navigate from handlers. */
    static class Buttons extends Component<Buttons.Props> {
        record Props() {}
        public Element render(Props p) {
            Navigator nav = useNavigate();
            SearchState search = useSearchParams();
            return el("div", null,
                    el("button", new Attrs().attr("id", "go-edit").on("onClick", e -> nav.navigate("edit")), "edit"),
                    el("button", new Attrs().attr("id", "go-up").on("onClick", e -> nav.navigate("..")), "up"),
                    el("button", new Attrs().attr("id", "go-back").on("onClick", e -> nav.back()), "back"),
                    el("button", new Attrs().attr("id", "tag-java").on("onClick", e -> search.set(search.params().with("tag", "java"))), "java"),
                    el("button", new Attrs().attr("id", "tag-none").on("onClick", e -> search.replace(search.params().without("tag"))), "clear"),
                    el("span", new Attrs().attr("id", "tag"), String.valueOf(search.params().get("tag"))));
        }
        static Element make() { return Jsx.c(Buttons.class, Buttons::new, null, new Props()); }
    }

    // ================================================================ fake DOM (like DomTest's)

    static final class FNode {
        final String tag; String text; Object data; FNode parent;
        final Map<String, String> attrs = new LinkedHashMap<>();
        final List<FNode> children = new ArrayList<>();
        FNode(String tag, String text) { this.tag = tag; this.text = text; }
        String html() {
            if (tag == null) return text;
            StringBuilder sb = new StringBuilder("<").append(tag);
            attrs.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
            sb.append('>');
            children.forEach(c -> sb.append(c.html()));
            return sb.append("</").append(tag).append('>').toString();
        }
        FNode find(String id) {
            if (id.equals(attrs.get("id"))) return this;
            for (FNode c : children) { FNode f = c.find(id); if (f != null) return f; }
            return null;
        }
        FNode firstTag(String t) {
            if (t.equals(tag)) return this;
            for (FNode c : children) { FNode f = c.firstTag(t); if (f != null) return f; }
            return null;
        }
    }

    static final class FakeDom implements DomOps<FNode> {
        final Map<String, Consumer<NativeEvent<FNode>>> listeners = new LinkedHashMap<>();
        public FNode createElement(String tag) { return new FNode(tag, null); }
        public FNode createTextNode(String text) { return new FNode(null, text); }
        public void setAttribute(FNode e, String n, String v) { e.attrs.put(n, v); }
        public void removeAttribute(FNode e, String n) { e.attrs.remove(n); }
        public void setProperty(FNode e, String n, Object v) { }
        public void setStyleProperty(FNode e, String n, String v) { }
        public void setTextContent(FNode t, String text) { t.text = text; }
        public void appendChild(FNode p, FNode c) { detach(c); p.children.add(c); c.parent = p; }
        public void insertBefore(FNode p, FNode c, FNode before) { detach(c); p.children.add(p.children.indexOf(before), c); c.parent = p; }
        public void removeChild(FNode p, FNode c) { detach(c); }
        public FNode parentNode(FNode n) { return n.parent; }
        public Object getData(FNode n) { return n.data; }
        public void setData(FNode n, Object d) { n.data = d; }
        public void addEventListener(FNode container, String type, Consumer<NativeEvent<FNode>> l) { listeners.put(type, l); }
        private static void detach(FNode c) { if (c.parent != null) { c.parent.children.remove(c); c.parent = null; } }
    }

    record Click(FNode target, boolean modified, boolean[] prevented) implements NativeEvent<FNode> {
        public String type() { return "click"; }
        public String targetTag() { return target.tag; }
        public String targetType() { return null; }
        public String value() { return null; }
        public boolean checked() { return false; }
        public void preventDefault() { prevented[0] = true; }
        public boolean modified() { return modified; }
    }

    static final class Env {
        final FakeDom dom = new FakeDom();
        final ManualSchedulerHost clock = new ManualSchedulerHost();
        final FNode container = new FNode("root", null);
        final DomRoot<FNode> root = DomRoot.create(dom, container, clock);

        void render(Element e) { root.render(e); clock.runUntilIdle(); }
        void settle() { clock.runUntilIdle(); }
        String html() { StringBuilder sb = new StringBuilder(); container.children.forEach(c -> sb.append(c.html())); return sb.toString(); }
        /** Returns whether the click was left to the browser (nobody called preventDefault). */
        boolean click(String id, boolean modified) {
            FNode target = container.children.get(0).find(id);
            if (target == null) throw new AssertionError("no #" + id + " in " + html());
            Click ev = new Click(target, modified, new boolean[1]);
            dom.listeners.get("click").accept(ev);
            clock.runUntilIdle();
            return !ev.prevented[0];
        }
        boolean click(String id) { return click(id, false); }
        String text(String id) {
            FNode n = container.children.get(0).find(id);
            if (n == null) throw new AssertionError("no #" + id + " in " + html());
            StringBuilder sb = new StringBuilder();
            collect(n, sb);
            return sb.toString();
        }
        private static void collect(FNode n, StringBuilder sb) { if (n.tag == null) sb.append(n.text); else n.children.forEach(c -> collect(c, sb)); }
    }

    static List<RouteObject> defs(Object... routes) {
        List<RouteObject> out = new ArrayList<>();
        for (Object o : routes) out.add((RouteObject) o);
        return out;
    }
    static RouteObject def(String path, RouteObject... children) { return new RouteObject(path, false, null, List.of(children)); }
    static RouteObject idx() { return new RouteObject(null, true, null, List.of()); }
    static String chain(List<RouteMatch> m) {
        if (m == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (RouteMatch x : m) sb.append(x.route().index() ? "index" : x.route().path() == null ? "layout" : x.route().path()).append(" ");
        return sb.toString().trim();
    }

    // ================================================================ tests

    public static void main(String[] args) {
        // ---------------------------------------------------------------- pure
        test("Location.parse splits path, search and hash", () -> {
            eq("/a/b|?x=1|#h", join(Location.parse("/a/b?x=1#h")));
            eq("/|?x=1|", join(Location.parse("?x=1")));
            eq("/a||", join(Location.parse("a")));
            eq("/||", join(Location.parse("")));
            eq("/p||#top", join(Location.parse("/p#top")));
            eq("/a/b?x=1#h", Location.parse("/a/b?x=1#h").toString());
        });

        test("Paths.resolve: absolute, relative, .., search-only", () -> {
            eq("/abs", Paths.resolve("/a/b", "/a/b/c", "/abs"));
            eq("/a/b/edit", Paths.resolve("/a/b", "/a/b", "edit"));
            eq("/a/c", Paths.resolve("/a/b", "/a/b", "../c"));
            eq("/", Paths.resolve("/a", "/a", ".."));
            eq("/", Paths.resolve("/", "/", "../.."));
            eq("/a/b", Paths.resolve("/a/b", "/a/b/c", "."));
            eq("/p?q=1", Paths.resolve("/a", "/p", "?q=1"));
            eq("/p#h", Paths.resolve("/a", "/p", "#h"));
            eq("/p", Paths.resolve("/a", "/p", "?"));
            eq("/p?a=1", Paths.resolve("/a", "/p", "?a=1#"));
            eq("/p#x", Paths.resolve("/a", "/p", "?#x"));
            eq("/a/x?y=1#z", Paths.resolve("/a", "/p", "x?y=1#z"));
            eq("/x/y", Paths.resolve("/", "/", "x//y/"));
        });

        test("Paths.startsWith / strip work on whole segments, ignoring case", () -> {
            check(Paths.startsWith("/docs/api", "/docs"), "below");
            check(Paths.startsWith("/DOCS", "/docs"), "case");
            check(!Paths.startsWith("/docsx", "/docs"), "not a segment boundary");
            check(Paths.startsWith("/a", "/"), "root");
            eq("/api/x", Paths.strip("/docs/api/x", "/docs"));
            eq("/", Paths.strip("/docs", "/docs"));
        });

        test("SearchParams: parse, get, encode, with/without", () -> {
            SearchParams p = SearchParams.parse("?tag=java&q=a+b%20c&tag=go&flag");
            eq("java", p.get("tag"));
            eq("[java, go]", p.getAll("tag"));
            eq("a b c", p.get("q"));
            eq("", p.get("flag"));
            check(p.get("none") == null && p.has("flag") && !p.has("none"), "has");
            eq("fallback", p.get("none", "fallback"));
            eq("q=a+b+c&tag=x%26y%3D%C3%A9", SearchParams.empty().with("q", "a b c").with("tag", "x&y=é").toString());
            eq("tag=java&tag=go&flag=", p.without("q").toString());
            check(p.with("tag", "").get("tag") == null, "empty removes");
            check(p.equals(SearchParams.parse(p.toString())), "round trip");
        });

        test("matcher: static beats dynamic beats splat, whatever the order", () -> {
            List<RouteObject> r = defs(def("*"), def("users/*"), def("users/:id"), def("users/new"));
            eq("users/new", chain(RouteMatcher.match(r, "/users/new")));
            eq("users/:id", chain(RouteMatcher.match(r, "/users/7")));
            eq("users/*", chain(RouteMatcher.match(r, "/users/7/x")));
            eq("*", chain(RouteMatcher.match(r, "/other")));
            eq("7", RouteMatcher.match(r, "/users/7").get(0).params().get("id"));
            eq("7/x", RouteMatcher.match(r, "/users/7/x").get(0).params().get("*"));
            eq("users/new", chain(RouteMatcher.match(r, "/USERS/New")));          // case-insensitive
            eq("a b", RouteMatcher.match(defs(def("files/:name")), "/files/a%20b").get(0).params().get("name"));
        });

        test("matcher: no match gives null, trailing slash is ignored", () -> {
            check(RouteMatcher.match(defs(def("a")), "/b") == null, "no match");
            check(RouteMatcher.match(defs(def("a")), "/a/b") == null, "prefix is not enough for a leaf");
            eq("a", chain(RouteMatcher.match(defs(def("a")), "/a/")));
        });

        test("matcher: layout, index and nested routes", () -> {
            List<RouteObject> r = defs(def("/", idx(), def("posts", idx(), def(":id")), def("*")), def("about"));
            eq("/ index", chain(RouteMatcher.match(r, "/")));
            eq("/ posts index", chain(RouteMatcher.match(r, "/posts")));
            eq("/ posts :id", chain(RouteMatcher.match(r, "/posts/42")));
            eq("/ *", chain(RouteMatcher.match(r, "/nope")));
            eq("about", chain(RouteMatcher.match(r, "/about")));
            List<RouteMatch> m = RouteMatcher.match(r, "/posts/42");
            eq("/ /posts /posts/42", m.get(0).pathnameBase() + " " + m.get(1).pathnameBase() + " " + m.get(2).pathnameBase());
            eq("42", m.get(2).params().get("id"));
            check(m.get(1).params().isEmpty(), "parents do not see child params");
            // a route without a path is a layout: it wraps its children but is never matched alone
            List<RouteObject> layout = defs(new RouteObject(null, false, null, List.of(def("a"), def("b"))));
            eq("layout a", chain(RouteMatcher.match(layout, "/a")));
            check(RouteMatcher.match(layout, "/") == null, "layout alone");
        });

        test("matcher: absolute child paths must lie under the parent", () -> {
            eq("users /users/:id", chain(RouteMatcher.match(defs(def("users", def("/users/:id"))), "/users/3")));
            boolean threw = false;
            try { RouteMatcher.match(defs(def("users", def("/other"))), "/users"); } catch (IllegalArgumentException e) { threw = true; }
            check(threw, "absolute path outside the parent accepted");
        });

        test("MemoryHistory: push, replace, go, listeners", () -> {
            MemoryHistory h = new MemoryHistory("/a");
            List<String> seen = new ArrayList<>();
            Runnable stop = h.listen(() -> seen.add(h.location().toString()));
            h.push("/b?x=1"); h.push("/c"); h.go(-1); h.replace("/d"); h.go(5); h.go(-9);
            eq("[/b?x=1, /c, /b?x=1, /d, /c, /a]", seen.toString());   // go(5) stops at the last entry (/c), go(-9) at the first
            stop.run();
            h.push("/z");
            eq(6, seen.size());
            h.go(-1); h.push("/y");
            h.go(1);                                  // the forward entry was dropped by push
            eq("/y", h.location().toString());
        });

        // ---------------------------------------------------------------- rendering on the JVM
        test("SSR: matching, params, nested layout with Outlet, 404", () -> {
            MemoryHistory h = new MemoryHistory("/");
            Element app = router(h, null, routes(
                    route("/", Layout.make("root"),
                            index(Page.make("home")),
                            route("posts/:id", Page.make("post")),
                            route("*", Page.make("missing")))));
            eq("<div class=\"root\"><h1>root</h1><p id=\"home\">home {} /</p></div>", ServerRenderer.renderToString(app));
            h.push("/posts/42?tag=a#x");
            eq("<div class=\"root\"><h1>root</h1><p id=\"post\">post {id=42} /posts/42?tag=a#x</p></div>", ServerRenderer.renderToString(app));
            h.push("/nope");
            check(ServerRenderer.renderToString(app).contains("id=\"missing\""), "404 route");
            Element noMatch = router(new MemoryHistory("/x"), null, routes(route("/y", Page.make("y"))));
            eq("", ServerRenderer.renderToString(noMatch));
        });

        test("SSR: Link href and NavLink active state (basename, relative, end)", () -> {
            MemoryHistory h = new MemoryHistory("/app/posts/7");
            Element app = router(h, "/app", routes(route("/", null,
                    route("posts/*", el("nav", null,
                            link("edit", "e"), link("../list", "l"), link("/about?x=1", "a"),
                            navLink("/posts", false, "p"), navLink("/posts", true, "pe"), navLink("/", false, "home"))))));
            eq("<nav><a href=\"/app/posts/edit\">e</a>"
                    + "<a href=\"/app/list\">l</a><a href=\"/app/about?x=1\">a</a>"
                    + "<a href=\"/app/posts\" class=\"nav active\" aria-current=\"page\">p</a>"
                    + "<a href=\"/app/posts\" class=\"nav\">pe</a><a href=\"/app/\" class=\"nav\">home</a></nav>",
                    ServerRenderer.renderToString(app));
        });

        test("hooks outside a Router explain themselves", () -> {
            boolean threw = false;
            try { ServerRenderer.renderToString(Page.make("x")); } catch (IllegalStateException e) { threw = e.getMessage().contains("<Router>"); }
            check(threw, "no hint");
            threw = false;
            try { ServerRenderer.renderToString(router(new MemoryHistory(), null, el("div", null, routes(el("p", null, "not a route"))))); }
            catch (IllegalArgumentException e) { threw = e.getMessage().contains("<Route>"); }
            check(threw, "non-Route child accepted");
        });

        // ---------------------------------------------------------------- in a (fake) DOM
        test("Link click navigates without a reload; modified clicks and target=_blank are left to the browser", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/");
            e.render(router(h, null, el("div", null,
                    el("nav", null, link("/a", "A"), navLink("/b", false, "B")),
                    routes(route("/a", Page.make("a")), route("/b", Page.make("b")), route("/", el("i", null, "home"))))));
            eq("<root><div><nav><a href=\"/a\">A</a><a href=\"/b\" class=\"nav\">B</a></nav><i>home</i></div></root>", "<root>" + e.html() + "</root>");
            FNode a = e.container.children.get(0).firstTag("a");
            Click plain = new Click(a, false, new boolean[1]);
            e.dom.listeners.get("click").accept(plain);
            e.settle();
            check(plain.prevented[0], "plain click must be handled by the router");
            eq("/a", h.location().toString());
            check(e.html().contains("id=\"a\""), "page a: " + e.html());
            Click ctrl = new Click(a, true, new boolean[1]);
            e.dom.listeners.get("click").accept(ctrl);
            check(!ctrl.prevented[0], "ctrl-click must be left to the browser");
        });

        test("history changes from outside (back button, push) re-render", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/a");
            e.render(router(h, null, routes(route("/a", Page.make("a")), route("/b", Page.make("b")))));
            check(e.html().contains("id=\"a\""), "start");
            h.push("/b"); e.settle();
            check(e.html().contains("id=\"b\"") && !e.html().contains("id=\"a\""), "push: " + e.html());
            h.go(-1); e.settle();
            check(e.html().contains("id=\"a\""), "back: " + e.html());
            e.root.unmount();
            h.push("/b");                          // unmounted: the listener is gone, nothing may throw
            e.settle();
        });

        test("useNavigate resolves against the route it is used in", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/items/5");
            e.render(router(h, null, routes(route("items/:id", Buttons.make()), route("items/:id/edit", Page.make("edit")), route("items", Page.make("items")))));
            e.click("go-edit");
            eq("/items/5/edit", h.location().toString());
            check(e.html().contains("id=\"edit\"") && e.html().contains("{id=5}"), "edit page with params: " + e.html());
            h.go(-1); e.settle();
            e.click("go-up");
            eq("/items", h.location().toString());
            check(e.html().contains("id=\"items\""), "up: " + e.html());
        });

        test("useSearchParams: set adds an entry, replace does not; the path stays", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/p");
            e.render(router(h, null, routes(route("p", Buttons.make()))));
            eq("null", e.text("tag"));
            e.click("tag-java");
            eq("/p?tag=java", h.location().toString());
            eq("java", e.text("tag"));
            e.click("tag-none");                    // replace: the entry /p?tag=java becomes /p
            eq("/p", h.location().toString());
            eq("null", e.text("tag"));
            h.go(-1); e.settle();                   // had it been a push, back would show /p?tag=java
            eq("/p", h.location().toString());
        });

        test("<Navigate replace> redirects on mount and leaves no entry behind", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/start");
            h.push("/secret");
            e.render(router(h, null, routes(
                    route("secret", navigate("/login?next=%2Fsecret", true)),
                    route("login", Page.make("login")))));
            e.settle();
            eq("/login?next=%2Fsecret", h.location().toString());
            check(e.html().contains("id=\"login\""), "login page: " + e.html());
            h.go(-1); e.settle();
            eq("/start", h.location().toString());   // /secret was replaced, not kept
        });

        test("basename: links and navigation carry it, routes do not see it", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/docs/a");
            e.render(router(h, "/docs", el("div", null, link("/b", "B"), routes(route("a", Page.make("a")), route("b", Page.make("b"))))));
            check(e.html().contains("href=\"/docs/b\"") && e.html().contains("id=\"a\""), e.html());
            FNode a = e.container.children.get(0).firstTag("a");
            e.dom.listeners.get("click").accept(new Click(a, false, new boolean[1]));
            e.settle();
            eq("/docs/b", h.location().toString());
            check(e.html().contains("id=\"b\""), e.html());
            h.push("/elsewhere"); e.settle();
            eq("", e.html());                         // outside the basename: nothing to show
        });

        test("a nested <Routes> matches the rest of a splat route", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/account/billing");
            e.render(router(h, null, routes(route("account/*",
                    el("div", null, el("h2", null, "account"), routes(route("billing", Page.make("billing")), route("settings", Page.make("settings"))))))));
            check(e.html().contains("<h2>account</h2>") && e.html().contains("id=\"billing\""), e.html());
            h.push("/account/settings"); e.settle();
            check(e.html().contains("id=\"settings\"") && !e.html().contains("billing"), e.html());
            h.push("/account/zzz"); e.settle();
            check(e.html().contains("<h2>account</h2>") && !e.html().contains("<p"), "nested no match: " + e.html());
        });

        test("state below a route survives navigation between child routes of one layout", () -> {
            Env e = new Env();
            MemoryHistory h = new MemoryHistory("/a");
            e.render(router(h, null, routes(route("/", Counter.make(), route("a", Page.make("a")), route("b", Page.make("b"))))));
            e.click("inc"); e.click("inc");
            eq("2", e.text("count"));
            h.push("/b"); e.settle();
            eq("2", e.text("count"));                 // the layout was not remounted
        });

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        if (!failures.isEmpty()) System.exit(1);
    }

    static class Counter extends Component<Counter.Props> {
        record Props() {}
        public Element render(Props p) {
            jreact.State<Integer> n = useState(0);
            return el("section", null,
                    el("button", new Attrs().attr("id", "inc").on("onClick", e -> n.update(x -> x + 1)), "+"),
                    el("b", new Attrs().attr("id", "count"), String.valueOf(n.get())),
                    outlet());
        }
        static Element make() { return Jsx.c(Counter.class, Counter::new, null, new Props()); }
    }

    // ================================================================ mini harness

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    static String join(Location l) { return l.pathname() + "|" + l.search() + "|" + l.hash(); }

    static void test(String name, Runnable body) {
        try { body.run(); passed++; }
        catch (Throwable t) { failures.add(name + ": " + t); t.printStackTrace(System.out); }
    }

    static void eq(Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError("expected: " + expected + "\n  actual: " + actual);
        }
    }

    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}

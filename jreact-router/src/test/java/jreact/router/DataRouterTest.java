package jreact.router;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.dom.NativeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jreact.router.RouterHooks.*;

/** Tests of the data router: DataRouter on its own (no DOM), then RouterProvider with Link, Form and hooks in a fake DOM. */
public final class DataRouterTest {

    // ================================================================ helpers

    static int counter;
    static final List<String> log = new ArrayList<>();

    static RouteObject r(String path) { return RouteObject.of(path); }
    static Loader count(String name, Object value) { return a -> { log.add(name); return value; }; }
    static Object data(DataRouter router, String id) { return router.state().loaderData().get(id); }
    static String path(DataRouter router) { return router.state().location().toString(); }
    static ErrorResponse errorOf(DataRouter router, String id) { return (ErrorResponse) router.state().errors().get(id); }

    /** Routes: "/" layout (id root) with home, posts (list, loader) and posts/:id (loader). */
    static List<RouteObject> blogRoutes(List<Promise<Object>> gate) {
        return List.of(r("/").id("root").loader(count("root", "R")).errorElement(null).children(
                RouteObject.indexRoute().id("home").loader(count("home", "H")),
                r("posts").id("posts").loader(a -> { log.add("posts?" + a.request().searchParams().get("q")); return "P"; }),
                r("posts/:id").id("post").loader(a -> {
                    log.add("post" + a.params().get("id"));
                    if (a.params().get("id").equals("boom")) throw new IllegalStateException("boom");
                    if (a.params().get("id").equals("old")) throw Redirect.to("/posts/new");
                    if (a.params().get("id").equals("slow")) { Promise<Object> p = Promise.pending(); gate.add(p); return p; }
                    return "post-" + a.params().get("id");
                })));
    }

    // ================================================================ main

    public static void main(String[] args) {
        // ---------------------------------------------------------------- DataRouter alone
        test("the first page's loaders run at creation and the data is there", () -> {
            log.clear();
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/posts/7");
            check(router.state().initialized(), "initialized");
            eq("R", data(router, "root"));
            eq("post-7", data(router, "post"));
            eq("[root, post7]", log);
            eq("IDLE", router.state().navigation().state());
        });

        test("a loader that waits keeps the old page, navigation shows LOADING, resolving commits and writes the URL", () -> {
            List<Promise<Object>> gate = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(blogRoutes(gate), "/");
            History h = router.history();
            router.navigate("/posts/slow", false);
            eq("LOADING", router.state().navigation().state());
            eq("/posts/slow", router.state().navigation().location().toString());
            eq("/", path(router));                         // still the old page
            eq("/", h.location().toString());              // and the URL has not changed yet
            gate.get(0).resolve("late");
            eq("/posts/slow", path(router));
            eq("/posts/slow", h.location().toString());
            eq("late", data(router, "post"));
            eq("IDLE", router.state().navigation().state());
        });

        test("only new or changed routes load; a query change reloads all; shouldRevalidate can veto", () -> {
            log.clear();
            DataRouter router = Routers.createMemoryRouter(List.of(
                    r("/").id("root").loader(count("root", "R")).shouldRevalidate(a -> false).children(
                            r("posts").id("posts").loader(a -> { log.add("posts?" + a.request().searchParams().get("q")); return "P"; }),
                            r("posts/:id").id("post").loader(a -> { log.add("post" + a.params().get("id")); return "x"; }))), "/posts");
            eq("[root, posts?null]", log);
            log.clear();
            router.navigate("/posts/1", false);
            eq("[post1]", log);                            // root is not asked again
            log.clear();
            router.navigate("/posts/2", false);
            eq("[post2]", log);                            // other params: the route reloads
            log.clear();
            router.navigate("/posts/2?x=1", false);
            eq("[post2]", log);                            // same params, new query: reloads (root vetoes)
            log.clear();
            router.navigate("/posts/2?x=1#top", false);
            eq("[]", log);                                 // only the hash changed
        });

        test("a newer navigation cancels the older one: its late result is ignored and its signal aborted", () -> {
            List<Promise<Object>> gate = new ArrayList<>();
            boolean[] aborted = {false};
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").children(
                    RouteObject.indexRoute().id("home"),
                    r("slow").id("slow").loader(a -> { a.signal().onAbort(() -> aborted[0] = true); Promise<Object> p = Promise.pending(); gate.add(p); return p; }),
                    r("fast").id("fast").loader(a -> "fast!"))), "/");
            router.navigate("/slow", false);
            router.navigate("/fast", false);
            check(aborted[0], "the first navigation was aborted");
            eq("/fast", path(router));
            gate.get(0).resolve("too late");
            eq("/fast", path(router));
            check(!router.state().loaderData().containsKey("slow"), "no data of the cancelled page");
        });

        test("a redirect from a loader ends at the target with one history entry", () -> {
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/");
            router.navigate("/posts/old", false);
            eq("/posts/new", path(router));
            eq("post-new", data(router, "post"));
            router.history().go(-1);
            eq("/", path(router));                         // one back leads to where we came from, not to /posts/old
        });

        test("a loader error goes to the nearest errorElement; parents keep their data, the failing route has none", () -> {
            Element boundary = Jsx.el("div", null, null, "oops");
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").loader(count("root", "R")).children(
                    r("posts").id("posts").errorElement(boundary).children(
                            r(":id").id("post").loader(a -> { throw new IllegalStateException("boom"); })))), "/");
            router.navigate("/posts/1", false);
            check(router.state().errors().containsKey("posts"), "error on the posts boundary: " + router.state().errors());
            eq("boom", ((Throwable) router.state().errors().get("posts")).getMessage());
            eq("R", data(router, "root"));
            check(!router.state().loaderData().containsKey("post"), "no data for the failed route");
        });

        test("an error without any errorElement lands on the root route; an unknown URL is a 404 ErrorResponse", () -> {
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/");
            router.navigate("/posts/boom", false);
            check(router.state().errors().containsKey("root"), "root shows it");
            router.navigate("/nowhere", false);
            eq(404, errorOf(router, "root").status());
            check(ErrorResponse.isErrorResponse(router.state().errors().get("root")), "isErrorResponse");
            router.navigate("/posts", false);
            eq(0, router.state().errors().size());          // a good page clears the error
        });

        test("an action runs for a POST, then the loaders run again; actionData holds the result", () -> {
            log.clear();
            List<String> saved = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").children(
                    r("todos").id("todos").loader(a -> { log.add("load"); return List.copyOf(saved); })
                            .action(a -> { saved.add(a.request().form().get("title")); return "saved"; }))), "/todos");
            eq("[load]", log);
            router.submit("/todos", "post", "title=milk", false);
            eq("[load, load]", log);
            eq("[milk]", data(router, "todos"));
            eq("saved", router.state().actionData().get("todos"));
            eq("POST", "POST");
            eq("/todos", path(router));
        });

        test("a redirect from an action navigates; a missing action is a 405; an action error shows at the boundary", () -> {
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").children(
                    r("new").id("new").action(a -> Redirect.to("/done")),
                    r("done").id("done").loader(a -> "ok"),
                    r("fail").id("fail").action(a -> { throw new IllegalStateException("denied"); }),
                    r("plain").id("plain"))), "/");
            router.submit("/new", "post", "", false);
            eq("/done", path(router));
            router.submit("/plain", "post", "", false);
            eq(405, errorOf(router, "root").status());
            router.submit("/fail", "post", "", false);
            eq("denied", ((Throwable) router.state().errors().get("root")).getMessage());
        });

        test("a submission to / reaches the layout's action; only ?index reaches the index route's", () -> {
            List<String> seen = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").action(a -> { seen.add("layout" + a.request().url().search()); return "L"; }).children(
                    RouteObject.indexRoute().id("home").action(a -> { seen.add("index" + a.request().url().search()); return "I"; }))), "/");
            router.submit("/", "post", "", false);
            router.submit("/?index", "post", "", false);
            eq("[layout, index]", seen);                    // the marker is not shown to the action
        });

        test("loaders and actions may return null", () -> {
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").loader(a -> null).action(a -> null)), "/");
            router.submit("/", "post", "", false);
            check(router.state().initialized() && router.state().errors().isEmpty(), String.valueOf(router.state().errors()));
        });

        test("lazy: the route's parts arrive when it is first matched; the navigation is LOADING meanwhile; it runs once", () -> {
            List<Promise<RouteObject>> gate = new ArrayList<>();
            int[] runs = {0};
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").children(
                    RouteObject.indexRoute().id("home"),
                    r("reports").id("reports").lazy(() -> { runs[0]++; Promise<RouteObject> p = Promise.pending(); gate.add(p); return p; }))), "/");
            router.navigate("/reports", false);
            eq("LOADING", router.state().navigation().state());
            eq("/", path(router));
            gate.get(0).resolve(RouteObject.layout().loader(a -> "report-data"));
            eq("/reports", path(router));
            eq("report-data", data(router, "reports"));
            router.navigate("/", false);
            router.navigate("/reports", false);
            eq("report-data", data(router, "reports"));
            eq(1, runs[0]);
        });

        test("lazy: what the route defines itself wins; a failing lazy lands on the error boundary", () -> {
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").children(
                    r("a").id("a").loader(x -> "mine").lazy(() -> Promise.resolved(RouteObject.layout().loader(x -> "lazy"))),
                    r("b").id("b").lazy(() -> { throw new IllegalStateException("nope"); }))), "/a");
            eq("mine", data(router, "a"));
            router.navigate("/b", false);
            eq("nope", ((Throwable) router.state().errors().get("root")).getMessage());
        });

        test("lazy: a fetcher can call the loader of a lazy route", () -> {
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").children(
                    RouteObject.indexRoute().id("home"),
                    r("x").id("x").lazy(() -> Promise.resolved(RouteObject.layout().loader(a -> "from-lazy"))))), "/");
            router.fetchLoad("f", "/x");
            eq("from-lazy", router.state().fetchers().get("f").data());
        });

        test("a pathless layout between does not hide the action of the route above it", () -> {
            List<String> seen = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").action(a -> { seen.add("root"); return null; }).children(
                    RouteObject.layout().id("layout").children(RouteObject.indexRoute().id("home")))), "/");
            router.submit("/", "post", "", false);
            eq("[root]", seen);
            check(router.state().errors().isEmpty(), "no 405: " + router.state().errors());
        });

        test("a redirect from an action reloads the loaders on the page it lands on", () -> {
            int[] version = {0};
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").loader(a -> "v" + version[0]).action(a -> { version[0]++; return Redirect.to("/done"); }).children(
                    RouteObject.indexRoute().id("home"), r("done").id("done"))), "/");
            eq("v0", data(router, "root"));
            router.submit("/", "post", "", false);
            eq("/done", path(router));
            eq("v1", data(router, "root"));
        });

        test("a submitting fetcher stays LOADING with its form data until the page is reloaded (optimistic UI)", () -> {
            List<Promise<Object>> gate = new ArrayList<>();
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").loader(a -> { Promise<Object> p = Promise.pending(); gate.add(p); return p; }).children(
                    r("star").id("star").action(a -> "starred"))), "/star");
            gate.get(0).resolve("first");
            router.fetchSubmit("f", "/star", "post", "on=1");
            eq("LOADING", router.state().fetchers().get("f").state());
            eq("on=1", router.state().fetchers().get("f").formData());
            eq("starred", router.state().fetchers().get("f").data());
            gate.get(1).resolve("second");
            eq("IDLE", router.state().fetchers().get("f").state());
            eq("second", data(router, "root"));
        });

        test("GET submissions put the fields in the query", () -> {
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/");
            router.submit("/posts", "get", "q=fiber", false);
            eq("/posts?q=fiber", path(router));
        });

        test("back/forward is a navigation too: loaders run for the place the browser went to", () -> {
            log.clear();
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/");
            router.navigate("/posts/1", false);
            router.navigate("/posts/2", false);
            router.go(-1);
            eq("/posts/1", path(router));
            eq("post-1", data(router, "post"));
            eq("POP", router.state().historyAction());
        });

        test("revalidate() runs every loader again", () -> {
            log.clear();
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/posts/3");
            log.clear();
            router.revalidate();
            eq("[root, post3]", log);
            eq("/posts/3", path(router));
        });

        test("a fetcher loads without navigating; its submit reloads the page", () -> {
            log.clear();
            int[] stars = {0};
            DataRouter router = Routers.createMemoryRouter(List.of(r("/").id("root").loader(a -> "stars:" + stars[0]).children(
                    RouteObject.indexRoute().id("home"),
                    r("star").id("star").action(a -> ++stars[0]).loader(a -> "star-data"))), "/");
            router.fetchLoad("f1", "/star");
            eq("star-data", router.state().fetchers().get("f1").data());
            eq("/", path(router));
            router.fetchSubmit("f1", "/star", "post", "");
            eq("1", String.valueOf(router.state().fetchers().get("f1").data()));
            eq("stars:1", data(router, "root"));          // the page saw the change
            eq("/", path(router));
            router.deleteFetcher("f1");
            check(router.state().fetchers().isEmpty(), "fetcher gone");
        });

        test("a blocker holds a navigation until proceed(); reset() drops it", () -> {
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/");
            router.registerBlocker("b", a -> true);
            router.navigate("/posts", false);
            eq("/", path(router));
            check(router.state().blockers().get("b").blocked(), "blocked");
            eq("/posts", router.state().blockers().get("b").location().toString());
            router.state().blockers().get("b").reset().run();
            check(!router.state().blockers().get("b").blocked(), "reset");
            router.navigate("/posts", false);
            router.state().blockers().get("b").proceed().run();
            eq("/posts", path(router));
        });

        test("a basename is stripped from the URL and added back when it is written", () -> {
            MemoryHistory h = new MemoryHistory("/docs/posts/5");
            DataRouter router = new DataRouter(blogRoutes(new ArrayList<>()), h, "/docs");
            eq("/posts/5", path(router));
            router.navigate("/posts/6", false);
            eq("/docs/posts/6", h.location().toString());
        });

        test("subscribers hear every change; dispose stops the router", () -> {
            DataRouter router = Routers.createMemoryRouter(blogRoutes(new ArrayList<>()), "/");
            int[] n = {0};
            router.subscribe(s -> n[0]++);
            router.navigate("/posts", false);
            check(n[0] >= 2, "LOADING and the commit: " + n[0]);
            router.dispose();
            router.history().push("/posts/9");
            eq("/posts", path(router));
        });

        test("Promise: then, compose, recover, all, settle once", () -> {
            Promise<Integer> p = Promise.pending();
            Promise<Integer> doubled = p.then(x -> x * 2);
            p.resolve(21);
            check(!p.resolve(5), "second resolve ignored");
            eq(42, doubled.value());
            eq("fixed", Promise.<String>rejected(new RuntimeException("x")).recover(t -> "fixed").value());
            Promise<List<Object>> all = Promise.all(List.of(Promise.resolved(1), Promise.resolved("b")));
            eq("[1, b]", all.value());
            check(Promise.rejected(new RuntimeException("x")).then(v -> 1).isRejected(), "rejection passes through then");
        });

        // ---------------------------------------------------------------- RouterProvider in a fake DOM
        RouterTestUi.run();

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        if (!failures.isEmpty()) System.exit(1);
    }

    // ================================================================ harness (shared with RouterTestUi)

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void test(String name, Runnable body) {
        try { body.run(); passed++; }
        catch (Throwable t) { failures.add(name + ": " + t); t.printStackTrace(System.out); }
    }

    static void eq(Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) throw new AssertionError("expected: " + expected + "\n  actual: " + actual);
    }

    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}

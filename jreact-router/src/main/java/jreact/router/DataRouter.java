package jreact.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The brain of a data router: knows the routes, keeps the URL, and runs <em>loaders</em> and <em>actions</em> around every
 * navigation, so a page renders only when its data is there. Created with {@code createBrowserRouter},
 * {@code createHashRouter} or {@link Routers#createMemoryRouter}, handed to {@code <RouterProvider router=.../>}. It has no
 * DOM and no UI, so it can be driven and tested without a browser: {@link #navigate}, {@link #submit}, {@link #state}.
 * <p>
 * How a navigation goes: (1) it starts, {@code navigation.state} is {@code LOADING} (or {@code SUBMITTING} for a form post)
 * while the old page stays on screen; (2) a form post runs the target route's action first; (3) the loaders of the new or
 * changed routes run in parallel ({@link ShouldRevalidate} can veto), the old data of unchanged routes is reused; (4) when all
 * are done the URL is written and the new page appears, in one step. A newer navigation cancels the older one
 * ({@link AbortSignal}). {@link Redirect} sends the navigation elsewhere, any other exception lands in the nearest
 * {@code errorElement} (the root one if there is none). After every action the loaders run again.
 * <p>
 * Everything runs on the thread that calls it (in the browser: the single JS thread); an unfinished {@link Promise} is how a
 * loader waits.
 */
public final class DataRouter {

    public enum NavigationState { IDLE, LOADING, SUBMITTING }

    /** {@code POP} (back/forward and the first load), {@code PUSH}, {@code REPLACE}. */
    public enum HistoryAction { POP, PUSH, REPLACE }

    /** What is going on right now. {@code location} is where it is heading. */
    public record Navigation(NavigationState state, Location location, String formMethod, String formAction, String formData) {
        public static final Navigation IDLE = new Navigation(NavigationState.IDLE, null, null, null, null);
    }

    /** One fetcher ({@code useFetcher}): a load or submission that does not navigate. */
    public record FetcherState(String key, NavigationState state, Object data, Object error, String formMethod, String formData) {}

    /** A navigation held back by {@code useBlocker}: {@code proceed} lets it through, {@code reset} cancels it. */
    public record Blocker(boolean blocked, Location location, Runnable proceed, Runnable reset) {
        public static final Blocker UNBLOCKED = new Blocker(false, null, () -> {}, () -> {});
    }

    public record BlockerArgs(Location currentLocation, Location nextLocation) {}

    /** Everything the UI needs, one immutable value per change. */
    public record Snapshot(Location location, HistoryAction historyAction, List<RouteMatch> matches, boolean initialized,
                           Map<String, Object> loaderData, Map<String, Object> actionData, Map<String, Object> errors,
                           Navigation navigation, boolean revalidating, Map<String, FetcherState> fetchers,
                           Map<String, Blocker> blockers, int version) {}

    private final History history;
    private final String basename;
    private final List<RouteObject> routes;
    private final List<Consumer<Snapshot>> subscribers = new ArrayList<>();
    private final Map<String, Predicate<BlockerArgs>> blockerChecks = new LinkedHashMap<>();
    private final Map<String, int[]> scrollPositions = new HashMap<>();
    private final Map<String, AbortSignal> fetcherSignals = new HashMap<>();
    private final Map<String, Integer> fetcherRuns = new HashMap<>();
    private Scroller scroller;
    private Snapshot state;
    private Runnable unlisten;
    private int navId;
    private int suppress;                 // > 0 while we write the history ourselves
    private AbortSignal navSignal;
    private boolean revalidateAfter;
    private final List<Runnable> afterCommit = new ArrayList<>();
    private boolean disposed;

    /**
     * @param routes   the route tree (ids are generated where missing)
     * @param history  where the URL lives
     * @param basename fixed prefix the app is served under, or null
     */
    public DataRouter(List<RouteObject> routes, History history, String basename) {
        this.routes = withIds(routes, "");
        this.history = history;
        this.basename = basename == null || basename.isEmpty() || basename.equals("/") ? "" : Paths.normalize(basename);
        Location start = currentLocation();
        this.state = new Snapshot(start, HistoryAction.POP, null, false, Map.of(), Map.of(), Map.of(), Navigation.IDLE, false,
                Map.of(), Map.of(), 0);
        this.unlisten = history.listen(this::onHistory);
        startNavigation(start, HistoryAction.POP, null, false, false, true);
    }

    // ================================================================ public API

    public Snapshot state() { return state; }

    public History history() { return history; }

    public String basename() { return basename; }

    /** The routes with their (possibly generated) ids. */
    public List<RouteObject> routes() { return routes; }

    /** Calls {@code listener} after every state change. Returns what stops it. */
    public Runnable subscribe(Consumer<Snapshot> listener) {
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    /** Scroll handling for {@link ScrollRestoration}; null (the default) leaves scrolling alone. */
    public void setScroller(Scroller scroller) { this.scroller = scroller; }

    public Scroller scroller() { return scroller; }

    /** Stops listening to the history and cancels what is running. */
    public void dispose() {
        disposed = true;
        if (unlisten != null) unlisten.run();
        unlisten = null;
        if (navSignal != null) navSignal.abort();
        fetcherSignals.values().forEach(AbortSignal::abort);
        subscribers.clear();
    }

    /**
     * Goes to {@code to}: an absolute path (without the basename) with optional query and hash. Relative targets are resolved by
     * {@code useNavigate()} before they get here.
     */
    public void navigate(String to, boolean replace) {
        Location target = Location.parse(to);
        HistoryAction action = replace ? HistoryAction.REPLACE : HistoryAction.PUSH;
        if (blockedBy(target, () -> startNavigation(target, action, null, true, false, false))) return;
        startNavigation(target, action, null, true, false, false);
    }

    /** Back/forward/go: the browser moves, the history tells us, the navigation follows. */
    public void go(int delta) { history.go(delta); }

    /**
     * Submits a form: {@code method} other than GET runs the action of the route that {@code to} resolves to, then reloads;
     * GET is a plain navigation to {@code to} with {@code body} as the query.
     * @param body URL-encoded fields ({@code name=Ada&tag=a})
     */
    public void submit(String to, String method, String body, boolean replace) {
        String m = method == null ? "GET" : method.toUpperCase();
        Location target = Location.parse(to);
        if (m.equals("GET")) {
            String q = body == null || body.isEmpty() ? "" : "?" + body;
            navigate(target.pathname() + q + target.hash(), replace);
            return;
        }
        Submission sub = new Submission(m, target.pathname() + target.search(), body == null ? "" : body);
        HistoryAction action = replace ? HistoryAction.REPLACE : HistoryAction.PUSH;
        if (blockedBy(target, () -> startNavigation(target, action, sub, true, false, false))) return;
        startNavigation(target, action, sub, true, false, false);
    }

    /** Runs all loaders of the current page again (after something changed on the server). */
    public void revalidate() {
        if (state.navigation().state() != NavigationState.IDLE) { revalidateAfter = true; return; }
        startNavigation(state.location(), state.historyAction(), null, false, true, false);
    }

    // ---------------------------------------------------------------- fetchers

    /** Loads the data of the route at {@code to} into the fetcher {@code key}, without navigating. */
    public void fetchLoad(String key, String to) {
        Location target = Location.parse(to);
        List<RouteMatch> matches = match(target);
        int run = beginFetcher(key);
        AbortSignal signal = fetcherSignals.get(key);
        if (matches == null) { fetcherDone(key, run, null, new ErrorResponse(404, "Not Found", target.pathname())); return; }
        RouteMatch leaf = matches.get(matches.size() - 1);
        setFetcher(new FetcherState(key, NavigationState.LOADING, fetcherData(key), null, null, null));
        if (leaf.route().loader() == null) { fetcherDone(key, run, null, new ErrorResponse(400, "Bad Request", "route " + leaf.route().id() + " has no loader")); return; }
        call(leaf.route().loader(), new DataArgs(leaf.params(), new Request("GET", target, ""), signal))
                .whenDone((v, e) -> fetcherFinished(key, run, target, v, e, false));
    }

    /** Submits to the action of the route at {@code to}, then reloads the page's loaders. The result is the fetcher's data. */
    public void fetchSubmit(String key, String to, String method, String body) {
        String m = method == null ? "POST" : method.toUpperCase();
        Location target = Location.parse(to);
        if (m.equals("GET")) { fetchLoad(key, target.pathname() + (body == null || body.isEmpty() ? target.search() : "?" + body)); return; }
        List<RouteMatch> matches = match(target);
        int run = beginFetcher(key);
        AbortSignal signal = fetcherSignals.get(key);
        if (matches == null) { fetcherDone(key, run, null, new ErrorResponse(404, "Not Found", target.pathname())); return; }
        RouteMatch leaf = actionLeaf(matches, target);
        setFetcher(new FetcherState(key, NavigationState.SUBMITTING, fetcherData(key), null, m, body));
        if (leaf.route().action() == null) { fetcherDone(key, run, null, new ErrorResponse(405, "Method Not Allowed", "route " + leaf.route().id() + " has no action")); return; }
        call(leaf.route().action(), new DataArgs(leaf.params(), new Request(m, withoutIndex(target), body == null ? "" : body), signal))
                .whenDone((v, e) -> fetcherFinished(key, run, target, v, e, true));
    }

    /** Forgets a fetcher (it cancels what it runs). {@code useFetcher} does this when its component goes away. */
    public void deleteFetcher(String key) {
        AbortSignal s = fetcherSignals.remove(key);
        if (s != null) s.abort();
        fetcherRuns.remove(key);
        if (state.fetchers().containsKey(key)) {
            Map<String, FetcherState> f = new LinkedHashMap<>(state.fetchers());
            f.remove(key);
            update(withFetchers(f));
        }
    }

    // ---------------------------------------------------------------- blockers

    /** Registers a blocker: while {@code shouldBlock} says true a navigation does not happen; the state shows a {@code Blocker} to decide. */
    public void registerBlocker(String key, Predicate<BlockerArgs> shouldBlock) {
        blockerChecks.put(key, shouldBlock);
    }

    public void unregisterBlocker(String key) {
        blockerChecks.remove(key);
        if (state.blockers().containsKey(key)) {
            Map<String, Blocker> b = new LinkedHashMap<>(state.blockers());
            b.remove(key);
            update(withBlockers(b));
        }
    }

    // ================================================================ navigation

    private record Submission(String method, String action, String body) {}

    private boolean blockedBy(Location next, Runnable proceedWith) {
        for (Map.Entry<String, Predicate<BlockerArgs>> e : new ArrayList<>(blockerChecks.entrySet())) {
            if (!e.getValue().test(new BlockerArgs(state.location(), next))) continue;
            String key = e.getKey();
            Runnable reset = () -> setBlocker(key, Blocker.UNBLOCKED);
            setBlocker(key, new Blocker(true, next, () -> { setBlocker(key, Blocker.UNBLOCKED); proceedWith.run(); }, reset));
            return true;
        }
        return false;
    }

    private void setBlocker(String key, Blocker b) {
        Map<String, Blocker> map = new LinkedHashMap<>(state.blockers());
        map.put(key, b);
        update(withBlockers(map));
    }

    private void onHistory() {
        if (suppress > 0 || disposed) return;
        Location now = currentLocation();
        boolean pending = state.navigation().state() != NavigationState.IDLE;
        // a notification about a place we already are at (a late hashchange after our own push): nothing to do
        if (!pending && now.toString().equals(state.location().toString())) return;
        startNavigation(now, HistoryAction.POP, null, false, false, false);
    }

    /** The history location without the basename (a place outside the basename becomes a path that matches nothing). */
    private Location currentLocation() {
        Location l = history.location();
        if (basename.isEmpty()) return l;
        if (!Paths.startsWith(l.pathname(), basename)) return new Location("/\u0000outside", "", "");
        return new Location(Paths.strip(l.pathname(), basename), l.search(), l.hash());
    }

    private List<RouteMatch> match(Location l) { return RouteMatcher.match(routes, l.pathname()); }

    private void startNavigation(Location to, HistoryAction action, Submission sub, boolean writeHistory, boolean force, boolean initial) {
        if (navSignal != null) navSignal.abort();
        AbortSignal signal = navSignal = new AbortSignal();
        int id = ++navId;
        if (scroller != null && !initial) scrollPositions.put(state.location().toString(), scroller.position());

        List<RouteMatch> matches = match(to);
        boolean revalidationOnly = force && !writeHistory && sub == null && to == state.location();
        if (matches == null) {
            // nothing matches: the first route's boundary shows a 404
            List<RouteMatch> stub = routes.isEmpty() ? null : List.of(new RouteMatch(routes.get(0), Map.of(), "/", "/"));
            Map<String, Object> errors = stub == null ? Map.of() : Map.of(routes.get(0).id(), new ErrorResponse(404, "Not Found", to.pathname()));
            commit(id, to, action, stub, Map.of(), Map.of(), errors, writeHistory);
            return;
        }

        if (revalidationOnly) update(new Snapshot(state.location(), state.historyAction(), state.matches(), state.initialized(), state.loaderData(),
                state.actionData(), state.errors(), state.navigation(), true, state.fetchers(), state.blockers(), state.version() + 1));
        else if (sub != null) update(withNavigation(new Navigation(NavigationState.SUBMITTING, to, sub.method(), sub.action(), sub.body())));
        else if (!initial) update(withNavigation(new Navigation(NavigationState.LOADING, to, null, null, null)));

        if (sub == null) { loadPhase(id, signal, to, action, matches, null, Map.of(), force, writeHistory); return; }

        RouteMatch leaf = actionLeaf(matches, to);
        if (leaf.route().action() == null) {
            ErrorResponse err = new ErrorResponse(405, "Method Not Allowed", "route \"" + leaf.route().id() + "\" has no action");
            commit(id, to, action, matches, keepData(matches, matches.size() - 1), Map.of(), Map.of(boundary(matches, matches.size() - 1), err), writeHistory);
            return;
        }
        call(leaf.route().action(), new DataArgs(leaf.params(), new Request(sub.method(), withoutIndex(to), sub.body()), signal)).whenDone((value, error) -> {
            if (id != navId) return;
            if (error instanceof Redirect r) { redirect(r, action, writeHistory, to, true); return; }
            if (error != null) {
                int last = matches.size() - 1;
                commit(id, to, action, matches, keepData(matches, last), Map.of(), Map.of(boundary(matches, last), error), writeHistory);
                return;
            }
            Map<String, Object> actionData = new LinkedHashMap<>();
            actionData.put(leaf.route().id(), value);
            loadPhase(id, signal, to, action, matches, sub, actionData, true, writeHistory);
        });
    }

    private void loadPhase(int id, AbortSignal signal, Location to, HistoryAction action, List<RouteMatch> matches, Submission sub,
                           Map<String, Object> actionData, boolean force, boolean writeHistory) {
        Snapshot cur = state;
        Object actionResult = actionData.isEmpty() ? null : actionData.values().iterator().next();
        int n = matches.size();
        @SuppressWarnings("unchecked") Promise<Object>[] pending = new Promise[n];
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            RouteMatch m = matches.get(i);
            String rid = m.route().id();
            RouteMatch before = find(cur.matches(), rid);
            boolean reusable = before != null && cur.loaderData().containsKey(rid) && !cur.errors().containsKey(rid);
            if (m.route().loader() == null) continue;
            boolean run = !reusable
                    || !before.pathname().equals(m.pathname()) || !before.params().equals(m.params())
                    || !cur.location().search().equals(to.search()) || sub != null || force;
            if (reusable && m.route().shouldRevalidate() != null) {
                run = m.route().shouldRevalidate().shouldRevalidate(new ShouldRevalidate.Args(cur.location(), to, before.params(), m.params(),
                        sub == null ? null : sub.method(), actionResult, run));
            }
            if (run) pending[i] = call(m.route().loader(), new DataArgs(m.params(), new Request("GET", to, ""), signal));
            else data.put(rid, cur.loaderData().get(rid));
        }
        List<Promise<?>> started = new ArrayList<>();
        for (Promise<Object> p : pending) if (p != null) started.add(p);
        boolean[] finished = {false};
        int[] left = {started.size()};
        Runnable done = () -> {
            if (id != navId || finished[0]) return;
            finished[0] = true;
            Map<String, Object> loaded = new LinkedHashMap<>(data);
            Map<String, Object> errors = new LinkedHashMap<>();
            int firstError = n;
            for (int i = 0; i < n; i++) {
                if (pending[i] == null) continue;
                Promise<Object> p = pending[i];
                if (p.isRejected()) {
                    String b = boundary(matches, i);
                    errors.putIfAbsent(b, p.error());
                    firstError = Math.min(firstError, indexOfId(matches, b));
                } else loaded.put(matches.get(i).route().id(), p.value());
            }
            if (firstError < n) for (int i = firstError; i < n; i++) loaded.remove(matches.get(i).route().id());
            commit(id, to, action, matches, loaded, actionData, errors, writeHistory);
        };
        if (started.isEmpty()) { done.run(); return; }
        for (Promise<?> p : started) {
            p.whenDone((v, e) -> {
                if (id != navId || finished[0]) return;
                if (e instanceof Redirect r) { finished[0] = true; signal.abort(); redirect(r, action, writeHistory, to, false); return; }
                if (--left[0] == 0) done.run();
            });
        }
    }

    private void redirect(Redirect r, HistoryAction action, boolean writeHistory, Location from, boolean afterAction) {
        String t = r.to();
        Location target = Location.parse(t.startsWith("/") ? t : Paths.resolve(from.pathname(), from.pathname(), t));
        // the page that redirected never got an entry: the target takes its place in the history (a back navigation: replace the entry it left)
        HistoryAction next = action == HistoryAction.POP ? HistoryAction.REPLACE : action;
        // after an action every loader runs again, also on the page the action redirected to
        startNavigation(target, next, null, true, afterAction, false);
    }

    private void commit(int id, Location to, HistoryAction action, List<RouteMatch> matches, Map<String, Object> loaderData,
                        Map<String, Object> actionData, Map<String, Object> errors, boolean writeHistory) {
        if (id != navId || disposed) return;
        navSignal = null;
        if (writeHistory) {
            suppress++;
            try {
                String url = basename + to;
                if (action == HistoryAction.REPLACE) history.replace(url); else history.push(url);
            } finally { suppress--; }
        }
        state = new Snapshot(to, action, matches, true, frozen(loaderData), frozen(actionData), frozen(errors), Navigation.IDLE,
                false, state.fetchers(), state.blockers(), state.version() + 1);
        emit();
        if (!afterCommit.isEmpty()) { List<Runnable> run = new ArrayList<>(afterCommit); afterCommit.clear(); run.forEach(Runnable::run); }
        if (revalidateAfter) { revalidateAfter = false; revalidate(); }
    }

    // ---------------------------------------------------------------- fetcher internals

    private int beginFetcher(String key) {
        AbortSignal old = fetcherSignals.put(key, new AbortSignal());
        if (old != null) old.abort();
        int run = fetcherRuns.merge(key, 1, Integer::sum);
        return run;
    }

    private Object fetcherData(String key) {
        FetcherState f = state.fetchers().get(key);
        return f == null ? null : f.data();
    }

    private void setFetcher(FetcherState f) {
        Map<String, FetcherState> map = new LinkedHashMap<>(state.fetchers());
        map.put(f.key(), f);
        update(withFetchers(map));
    }

    private void fetcherDone(String key, int run, Object data, Object error) {
        if (fetcherRuns.getOrDefault(key, 0) != run) return;
        setFetcher(new FetcherState(key, NavigationState.IDLE, data, error, null, null));
    }

    private void fetcherFinished(String key, int run, Location target, Object value, Throwable error, boolean submitted) {
        if (fetcherRuns.getOrDefault(key, 0) != run || disposed) return;
        if (error instanceof Redirect r) {
            setFetcher(new FetcherState(key, NavigationState.IDLE, fetcherData(key), null, null, null));
            navigate(r.to().startsWith("/") ? r.to() : Paths.resolve("/", target.pathname(), r.to()), false);
            return;
        }
        if (error != null) { fetcherDone(key, run, null, error); return; }
        if (!submitted) { fetcherDone(key, run, value, null); return; }
        // the fetcher stays "loading" (and keeps what it submitted: an optimistic UI) until the page has been reloaded
        FetcherState f = state.fetchers().get(key);
        setFetcher(new FetcherState(key, NavigationState.LOADING, value, null, f == null ? null : f.formMethod(), f == null ? null : f.formData()));
        afterCommit.add(() -> fetcherDone(key, run, value, null));
        revalidate();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The route that gets a submission: the deepest match, except that an index route only gets it when the URL has {@code ?index}
     * (a form on {@code /} posts to the layout's action, not to the index child's: same rule as react-router). When that route has no
     * action, the routes above it that match the very same path (layouts without a path of their own) are tried, nearest first.
     */
    private static RouteMatch actionLeaf(List<RouteMatch> matches, Location to) {
        int i = matches.size() - 1;
        if (matches.get(i).route().index() && !SearchParams.parse(to.search()).has("index") && i > 0) i--;
        int chosen = i;
        for (int j = i; j >= 0; j--) {
            if (matches.get(j).route().action() != null) return matches.get(j);
            if (j > 0 && !matches.get(j - 1).pathname().equals(matches.get(j).pathname())) break;
        }
        return matches.get(chosen);
    }

    /** The URL an action sees: without the {@code index} marker. */
    private static Location withoutIndex(Location l) {
        SearchParams p = SearchParams.parse(l.search());
        if (!p.has("index")) return l;
        SearchParams rest = p.without("index");
        return new Location(l.pathname(), rest.isEmpty() ? "" : "?" + rest, l.hash());
    }

    /** Runs a loader/action: always a promise, exceptions become rejections, a thrown or returned Redirect is a redirect. */
    private static Promise<Object> call(Object fn, DataArgs args) {
        Promise<Object> out = Promise.pending();
        try {
            Object result = fn instanceof Loader l ? l.load(args) : ((Action) fn).run(args);
            if (result instanceof Redirect r) { out.reject(r); return out; }
            if (result instanceof Promise<?> p) p.whenDone((v, e) -> {
                if (e != null) out.reject(e);
                else if (v instanceof Redirect r) out.reject(r);
                else out.resolve(v);
            });
            else out.resolve(result);
        } catch (Throwable t) {
            out.reject(t);
        }
        return out;
    }

    /** The id of the route that shows an error of {@code matches[index]}: the nearest one at or above it with an errorElement, else the first. */
    private static String boundary(List<RouteMatch> matches, int index) {
        for (int i = index; i >= 0; i--) if (matches.get(i).route().errorElement() != null) return matches.get(i).route().id();
        return matches.get(0).route().id();
    }

    private static int indexOfId(List<RouteMatch> matches, String id) {
        for (int i = 0; i < matches.size(); i++) if (matches.get(i).route().id().equals(id)) return i;
        return 0;
    }

    private static RouteMatch find(List<RouteMatch> matches, String id) {
        if (matches == null) return null;
        for (RouteMatch m : matches) if (m.route().id().equals(id)) return m;
        return null;
    }

    /** The loader data we still have for the routes above the failing one. */
    private Map<String, Object> keepData(List<RouteMatch> matches, int failing) {
        int stop = indexOfId(matches, boundary(matches, failing));
        Map<String, Object> keep = new LinkedHashMap<>();
        for (int i = 0; i < stop; i++) {
            String rid = matches.get(i).route().id();
            if (state.loaderData().containsKey(rid)) keep.put(rid, state.loaderData().get(rid));
        }
        return keep;
    }

    private static List<RouteObject> withIds(List<RouteObject> list, String prefix) {
        List<RouteObject> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            RouteObject r = list.get(i);
            String id = r.id() != null ? r.id() : prefix + i;
            out.add(r.id(id).children(withIds(r.children(), id + "-")));
        }
        return Collections.unmodifiableList(out);
    }

    private Snapshot withNavigation(Navigation n) {
        return new Snapshot(state.location(), state.historyAction(), state.matches(), state.initialized(), state.loaderData(), state.actionData(),
                state.errors(), n, state.revalidating(), state.fetchers(), state.blockers(), state.version() + 1);
    }

    private Snapshot withFetchers(Map<String, FetcherState> f) {
        return new Snapshot(state.location(), state.historyAction(), state.matches(), state.initialized(), state.loaderData(), state.actionData(),
                state.errors(), state.navigation(), state.revalidating(), Map.copyOf(f), state.blockers(), state.version() + 1);
    }

    private Snapshot withBlockers(Map<String, Blocker> b) {
        return new Snapshot(state.location(), state.historyAction(), state.matches(), state.initialized(), state.loaderData(), state.actionData(),
                state.errors(), state.navigation(), state.revalidating(), state.fetchers(), Map.copyOf(b), state.version() + 1);
    }

    /** An unmodifiable copy that, unlike Map.copyOf, accepts null values (a loader may return null). */
    private static <V> Map<String, V> frozen(Map<String, V> m) { return Collections.unmodifiableMap(new LinkedHashMap<>(m)); }

    private void update(Snapshot next) { state = next; emit(); }

    private void emit() { for (Consumer<Snapshot> s : new ArrayList<>(subscribers)) s.accept(state); }

    /** The scroll position saved when we left {@code location}, or null. */
    int[] savedScroll(Location location) { return scrollPositions.get(location.toString()); }
}

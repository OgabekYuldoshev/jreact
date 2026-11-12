package jreact.router;

/** {@link Navigator} on a {@link History}: resolves relative targets against a route path and adds the basename. */
final class RouterNavigator implements Navigator {
    private final History history;
    private final String basename;
    private final String base;
    private final DataRouter data;          // null under a plain <Router>

    RouterNavigator(History history, String basename, String base, DataRouter data) {
        this.history = history;
        this.basename = basename;
        this.base = base;
        this.data = data;
    }

    @Override public void navigate(String to) { navigate(to, false); }

    @Override
    public void navigate(String to, boolean replace) {
        if (data != null) { data.navigate(Paths.resolve(base, currentPathname(), to), replace); return; }
        String target = basename + Paths.resolve(base, currentPathname(), to);
        if (replace) history.replace(target); else history.push(target);
    }

    @Override public void back() { history.go(-1); }  // the history tells the router (POP)

    @Override public void forward() { history.go(1); }

    @Override public void go(int delta) { history.go(delta); }

    /** The live pathname without the basename, for targets like {@code ?a=1}. */
    private String currentPathname() {
        if (data != null) return data.state().location().pathname();
        String p = history.location().pathname();
        return basename.isEmpty() ? p : Paths.strip(p, basename);
    }
}

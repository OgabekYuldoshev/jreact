package jreact.router;

/** {@link Navigator} on a {@link History}: resolves relative targets against a route path and adds the basename. */
final class RouterNavigator implements Navigator {
    private final History history;
    private final String basename;
    private final String base;

    RouterNavigator(History history, String basename, String base) {
        this.history = history;
        this.basename = basename;
        this.base = base;
    }

    @Override public void navigate(String to) { navigate(to, false); }

    @Override
    public void navigate(String to, boolean replace) {
        String target = basename + Paths.resolve(base, currentPathname(), to);
        if (replace) history.replace(target); else history.push(target);
    }

    @Override public void back() { history.go(-1); }

    @Override public void forward() { history.go(1); }

    @Override public void go(int delta) { history.go(delta); }

    /** The live pathname without the basename, for targets like {@code ?a=1}. */
    private String currentPathname() {
        String p = history.location().pathname();
        return basename.isEmpty() ? p : Paths.strip(p, basename);
    }
}

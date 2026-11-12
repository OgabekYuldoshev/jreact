package jreact.router;

/** What {@code useSearchParams()} returns: the current query and a way to change it (the path and the router stay the same). */
public final class SearchState {
    private final SearchParams params;
    private final Navigator navigator;
    private final String pathname;

    SearchState(SearchParams params, Navigator navigator, String pathname) {
        this.params = params;
        this.navigator = navigator;
        this.pathname = pathname;
    }

    public SearchParams params() { return params; }

    /** Goes to the same path with these params, adding a history entry. */
    public void set(SearchParams next) { navigator.navigate(target(next), false); }

    /** Same, replacing the current history entry (good for a search box: typing must not fill the back button). */
    public void replace(SearchParams next) { navigator.navigate(target(next), true); }

    private String target(SearchParams next) { return pathname + (next.isEmpty() ? "" : "?" + next); }
}

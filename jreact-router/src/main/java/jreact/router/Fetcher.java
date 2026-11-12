package jreact.router;

/**
 * What {@code useFetcher()} returns: load data or submit to an action <em>without navigating</em> (a like button, a
 * live search box, a "mark as done" row). The page stays where it is; after a submission the page's loaders run again.
 * The same object identity does not hold across renders; its methods always work on the same fetcher.
 */
public final class Fetcher {
    private final DataRouter router;
    private final DataRouter.FetcherState state;
    private final String key;
    private final String base;
    private final String basePath;

    Fetcher(DataRouter router, String key, DataRouter.FetcherState state, String base, String basePath) {
        this.router = router;
        this.key = key;
        this.state = state;
        this.base = base;
        this.basePath = basePath;
    }

    public String key() { return key; }

    /** {@code IDLE}, {@code LOADING} (a load, or the reload after a submission) or {@code SUBMITTING}. */
    public DataRouter.NavigationState state() { return state == null ? DataRouter.NavigationState.IDLE : state.state(); }

    /** The last loader or action result, or null. */
    public Object data() { return state == null ? null : state.data(); }

    /** What went wrong the last time, or null. */
    public Object error() { return state == null ? null : state.error(); }

    /** URL-encoded fields of the submission in progress, or null. */
    public String formData() { return state == null ? null : state.formData(); }

    /** Calls the loader of the route that {@code to} resolves to ({@code /search?q=a}); the result is {@link #data()}. */
    public void load(String to) { router.fetchLoad(key, Paths.resolve(base, basePath, to)); }

    /** Calls the action of the route at {@code action} (relative targets resolve like {@code Link}); {@code method} default POST. */
    public void submit(String urlEncodedFields, String action, String method) {
        router.fetchSubmit(key, Paths.resolve(base, basePath, action == null ? "" : action), method, urlEncodedFields);
    }

    public void submit(String urlEncodedFields) { submit(urlEncodedFields, null, "POST"); }
}

package jreact.router;

/**
 * "Go there instead." Throw it or return it from a {@link Loader} or {@link Action}. The navigation that was running ends
 * at {@code to} (no history entry is left for the page that redirected).
 */
public final class Redirect extends RuntimeException {
    private final String to;

    private Redirect(String to) {
        super("redirect to " + to, null, false, false);       // no stack trace: it is control flow
        this.to = to;
    }

    public static Redirect to(String to) { return new Redirect(to); }

    public String to() { return to; }
}

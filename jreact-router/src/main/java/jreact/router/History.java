package jreact.router;

/**
 * The place a {@link Router} keeps its location: the browser's address bar ({@code BrowserHistory},
 * {@code HashHistory} in jreact-web) or a list in memory ({@link MemoryHistory}: tests, server rendering).
 */
public interface History {

    Location location();

    /** Goes to {@code to} (an absolute URL path such as {@code /posts?tag=a}) and adds a history entry. */
    void push(String to);

    /** Goes to {@code to} instead of the current entry. */
    void replace(String to);

    /** Moves in the history: {@code -1} is the back button. */
    void go(int delta);

    /** Calls {@code listener} after every location change (including back/forward). Returns what stops listening. */
    Runnable listen(Runnable listener);

    /** The value of an {@code <a href>} that leads to {@code to}: {@code to} itself, or {@code #to} for hash routing. */
    default String href(String to) { return to; }
}

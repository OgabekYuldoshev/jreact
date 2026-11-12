package jreact.router;

/** What {@code useNavigate()} returns: moves around the app from event handlers and effects. */
public interface Navigator {

    /** Goes to {@code to} and adds a history entry. {@code /x} is absolute, {@code edit} or {@code ../x} are relative to the current route, {@code ?a=1} keeps the path. */
    void navigate(String to);

    /** Same, but {@code replace} swaps the current history entry instead of adding one (redirects, login). */
    void navigate(String to, boolean replace);

    /** The browser's back button. */
    void back();

    void forward();

    void go(int delta);
}

package jreact.router;

/** The page's scroll position, for {@link ScrollRestoration}. The browser build has a real one; without it scrolling is left alone. */
public interface Scroller {
    /** Current {@code {x, y}}. */
    int[] position();

    void scrollTo(int x, int y);

    /** Scrolls the element with this id ({@code #top}, with or without the {@code #}) into view. */
    void toHash(String hash);
}

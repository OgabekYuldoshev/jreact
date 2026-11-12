package jreact.router;

/**
 * Where the app is: {@code /posts/7?tag=java#top} is pathname {@code /posts/7}, search {@code ?tag=java} (with the
 * {@code ?}, or empty) and hash {@code #top} (with the {@code #}, or empty).
 */
public record Location(String pathname, String search, String hash) {

    public static final Location ROOT = new Location("/", "", "");

    /** Splits a URL path ({@code /a/b?x=1#h}). A missing leading slash is added, an empty path is the root. */
    public static Location parse(String url) {
        String rest = url == null ? "" : url;
        String hash = "", search = "";
        int h = rest.indexOf('#');
        if (h >= 0) { hash = rest.substring(h); rest = rest.substring(0, h); }
        int q = rest.indexOf('?');
        if (q >= 0) { search = rest.substring(q); rest = rest.substring(0, q); }
        if (search.equals("?")) search = "";
        if (hash.equals("#")) hash = "";
        if (rest.isEmpty()) rest = "/";
        else if (rest.charAt(0) != '/') rest = "/" + rest;
        return new Location(rest, search, hash);
    }

    /** The URL path again: {@code pathname + search + hash}. */
    @Override public String toString() { return pathname + search + hash; }
}

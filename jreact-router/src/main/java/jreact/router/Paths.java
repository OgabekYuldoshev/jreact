package jreact.router;

import java.util.ArrayList;
import java.util.List;

/** Path arithmetic: segments, joining and resolving relative links like {@code ../edit}. */
public final class Paths {
    private Paths() {}

    /** {@code /a//b/} to {@code [a, b]}: empty segments are dropped. */
    static List<String> segments(String path) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '/') {
                if (i > start) out.add(path.substring(start, i));
                start = i + 1;
            }
        }
        return out;
    }

    /** {@code /a/b}: a leading slash, no trailing one (except for the root), no empty segments. */
    public static String normalize(String path) {
        List<String> s = segments(path);
        return s.isEmpty() ? "/" : "/" + String.join("/", s);
    }

    public static String join(String base, String path) { return normalize(base + "/" + path); }

    /**
     * Turns a link target into an absolute URL path.
     * <ul>
     *   <li>{@code /x}: absolute, unchanged</li>
     *   <li>{@code edit}, {@code ../list}, {@code .}: relative to {@code base} (the path of the current route)</li>
     *   <li>{@code ?tag=a}, {@code #top}: the current path ({@code currentPathname}) with a new search or hash</li>
     * </ul>
     * Search and hash of {@code to} are kept (a lone {@code ?} or {@code #} is dropped).
     */
    public static String resolve(String base, String currentPathname, String to) {
        int cut = to.length();
        int q = to.indexOf('?'), h = to.indexOf('#');
        if (q >= 0) cut = q;
        if (h >= 0 && h < cut) cut = h;
        String path = to.substring(0, cut), tail = to.substring(cut);
        while (tail.endsWith("?") || tail.endsWith("#")) tail = tail.substring(0, tail.length() - 1);   // "?" alone (an emptied query) is no query
        tail = tail.replace("?#", "#");
        if (path.isEmpty()) return normalize(currentPathname) + tail;
        List<String> out = path.charAt(0) == '/' ? new ArrayList<>() : new ArrayList<>(segments(base));
        for (String seg : segments(path)) {
            if (seg.equals("..")) { if (!out.isEmpty()) out.remove(out.size() - 1); }
            else if (!seg.equals(".")) out.add(seg);
        }
        return (out.isEmpty() ? "/" : "/" + String.join("/", out)) + tail;
    }

    /** True when {@code pathname} is {@code prefix} or lies below it ({@code /a/b} is under {@code /a}, {@code /ab} is not). Case-insensitive. */
    public static boolean startsWith(String pathname, String prefix) {
        List<String> p = segments(pathname), x = segments(prefix);
        if (x.size() > p.size()) return false;
        for (int i = 0; i < x.size(); i++) if (!p.get(i).equalsIgnoreCase(x.get(i))) return false;
        return true;
    }

    /** What is left of {@code pathname} after {@code prefix}: {@code /a/b/c} minus {@code /a} is {@code /b/c}. */
    public static String strip(String pathname, String prefix) {
        List<String> p = segments(pathname);
        int n = segments(prefix).size();
        return n >= p.size() ? "/" : "/" + String.join("/", p.subList(n, p.size()));
    }
}

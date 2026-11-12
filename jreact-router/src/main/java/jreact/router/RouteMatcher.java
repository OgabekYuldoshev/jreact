package jreact.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the route chain for a pathname, ranked like react-router v6: among all routes that match, the most specific
 * one wins ({@code /users/new} beats {@code /users/:id}, which beats {@code /users/*}; an index route beats its
 * layout), and on a tie the one defined first. Patterns: static segments (case-insensitive), {@code :name} params,
 * and a final {@code *} splat that takes the rest of the path (as the param {@code "*"}).
 */
public final class RouteMatcher {
    private RouteMatcher() {}

    private static final int STATIC = 10, DYNAMIC = 3, EMPTY = 1, SPLAT_PENALTY = -2, INDEX_BONUS = 2;

    private record Branch(List<RouteObject> routes, List<String> relativePaths, int score) {}

    /** The matches for {@code pathname}, outermost route first, or null when no route matches. */
    public static List<RouteMatch> match(List<RouteObject> routes, String pathname) {
        List<Branch> branches = new ArrayList<>();
        collect(routes, new ArrayList<>(), new ArrayList<>(), "", branches);
        List<Branch> ranked = new ArrayList<>(branches);
        ranked.sort(Comparator.comparingInt((Branch b) -> b.score()).reversed());   // stable: ties keep the definition order
        for (Branch b : ranked) {
            List<RouteMatch> m = matchBranch(b, pathname);
            if (m != null) return m;
        }
        return null;
    }

    // ---------------------------------------------------------------- branches

    private static void collect(List<RouteObject> routes, List<RouteObject> parents, List<String> parentRel, String parentPath,
                                List<Branch> out) {
        for (RouteObject r : routes) {
            String rel = r.path() == null ? "" : r.path();
            if (rel.startsWith("/")) {
                if (!Paths.startsWith(rel, parentPath)) {
                    throw new IllegalArgumentException("absolute child route \"" + rel + "\" must start with its parent's path \""
                            + parentPath + "\"");
                }
                rel = Paths.strip(rel, parentPath);
                if (rel.equals("/")) rel = "";
            }
            String full = Paths.join(parentPath, rel);
            List<RouteObject> chain = new ArrayList<>(parents); chain.add(r);
            List<String> rels = new ArrayList<>(parentRel); rels.add(rel);
            if (r.children() != null && !r.children().isEmpty()) {
                if (r.index()) throw new IllegalArgumentException("an index route cannot have child routes");
                collect(r.children(), chain, rels, full, out);
            }
            if (r.path() == null && !r.index()) continue;     // a layout route is only a parent, never an end point
            out.add(new Branch(chain, rels, score(full, r.index())));
        }
    }

    private static int score(String fullPath, boolean index) {
        List<String> segs = Paths.segments(fullPath);
        int score = Math.max(segs.size(), 1);
        if (segs.contains("*")) score += SPLAT_PENALTY;
        if (index) score += INDEX_BONUS;
        for (String s : segs) {
            if (s.equals("*")) continue;
            score += s.startsWith(":") ? DYNAMIC : STATIC;
        }
        if (segs.isEmpty()) score += EMPTY;
        return score;
    }

    // ---------------------------------------------------------------- matching

    private static List<RouteMatch> matchBranch(Branch branch, String pathname) {
        String matched = "/";
        Map<String, String> params = new LinkedHashMap<>();
        List<RouteMatch> out = new ArrayList<>();
        for (int i = 0; i < branch.routes().size(); i++) {
            boolean end = i == branch.routes().size() - 1;
            String remaining = matched.equals("/") ? pathname : Paths.strip(pathname, matched);
            PathMatch m = matchPath(branch.relativePaths().get(i), remaining, end);
            if (m == null) return null;
            params.putAll(m.params());
            out.add(new RouteMatch(branch.routes().get(i), Collections.unmodifiableMap(new LinkedHashMap<>(params)),
                    Paths.join(matched, m.pathname()), Paths.join(matched, m.pathnameBase())));
            if (!m.pathnameBase().equals("/")) matched = Paths.join(matched, m.pathnameBase());
        }
        return out;
    }

    /** What one pattern took from a pathname. */
    record PathMatch(Map<String, String> params, String pathname, String pathnameBase) {}

    /**
     * Matches {@code pattern} ({@code users/:id}, {@code files/*}, or empty) at the start of {@code pathname}.
     * With {@code end} the whole pathname must be used up, otherwise a prefix is enough (parents of other routes).
     */
    static PathMatch matchPath(String pattern, String pathname, boolean end) {
        List<String> p = Paths.segments(pattern), s = Paths.segments(pathname);
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < p.size(); i++) {
            String seg = p.get(i);
            if (seg.equals("*")) {
                if (i != p.size() - 1) throw new IllegalArgumentException("\"*\" can only be the last segment of \"" + pattern + "\"");
                String rest = i < s.size() ? String.join("/", s.subList(i, s.size())) : "";
                params.put("*", Uri.decode(rest, false));
                String base = i == 0 ? "/" : "/" + String.join("/", s.subList(0, i));
                return new PathMatch(params, Paths.normalize(pathname), base);
            }
            if (i >= s.size()) return null;
            if (seg.startsWith(":")) params.put(seg.substring(1), Uri.decode(s.get(i), false));
            else if (!seg.equalsIgnoreCase(s.get(i))) return null;
        }
        if (end && s.size() != p.size()) return null;
        String base = p.isEmpty() ? "/" : "/" + String.join("/", s.subList(0, p.size()));
        return new PathMatch(params, end ? Paths.normalize(pathname) : base, base);
    }
}

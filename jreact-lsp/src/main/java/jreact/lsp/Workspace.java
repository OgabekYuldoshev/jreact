package jreact.lsp;

import jreact.compiler.PropsIndex;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Everything the server knows about the project: the text of every .jsxj/.java file (open editors win over disk)
 * and the component index built from them. Not thread-safe by itself; {@link Server} guards it with a lock.
 */
final class Workspace {

    record Location(String uri, int line, int col) {}

    /** Test sources define throwaway components with the same simple names, and the real build does not index them. */
    private static final Set<String> SKIP = Set.of("node_modules", "target", "build", ".git", "out", ".idea", ".vscode", "test");
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private final List<Path> roots = new ArrayList<>();
    private final List<Path> extraSourceRoots = new ArrayList<>();
    final List<String> classpath = new ArrayList<>();
    private final Map<String, String> disk = new LinkedHashMap<>();
    private final Map<String, String> open = new LinkedHashMap<>();
    private PropsIndex index = new PropsIndex();
    private final Map<String, Location> classLocations = new HashMap<>();

    void addRoot(Path p) { roots.add(p.toAbsolutePath().normalize()); }

    /** Extra folders with .java sources that components live in or that javac should see (e.g. jreact-core). */
    void addSourceRoot(Path p) { extraSourceRoots.add(p.toAbsolutePath().normalize()); }

    // ------------------------------------------------------------ documents

    void scan() {
        disk.clear();
        List<Path> all = new ArrayList<>(roots);
        all.addAll(extraSourceRoots);
        for (Path root : all) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jsxj") || p.toString().endsWith(".java"))
                    .filter(p -> !skipped(root, p))
                    .forEach(p -> {
                        try { disk.put(p.toUri().toString(), Files.readString(p)); } catch (IOException | RuntimeException ignored) {}
                    });
            } catch (IOException ignored) {}
        }
        reindex();
    }

    private static boolean skipped(Path root, Path p) {
        for (Path part : root.relativize(p)) if (SKIP.contains(part.toString())) return true;
        return false;
    }

    void open(String uri, String text) { open.put(uri, text); reindex(); }

    void change(String uri, String text) { open.put(uri, text); reindex(); }

    void close(String uri) {
        open.remove(uri);
        Path p = path(uri);
        try { if (p != null && Files.exists(p)) disk.put(uri, Files.readString(p)); else disk.remove(uri); }
        catch (IOException e) { disk.remove(uri); }
        reindex();
    }

    String text(String uri) { String t = open.get(uri); return t != null ? t : disk.get(uri); }

    boolean isOpen(String uri) { return open.containsKey(uri); }

    List<String> openUris() { return new ArrayList<>(open.keySet()); }

    /** All known .jsxj files (uri to text), open editors overriding disk. */
    Map<String, String> jsxjFiles() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String uri : allUris()) if (uri.endsWith(".jsxj")) m.put(uri, text(uri));
        return m;
    }

    private Set<String> allUris() {
        Set<String> s = new java.util.LinkedHashSet<>(disk.keySet());
        s.addAll(open.keySet());
        return s;
    }

    // ------------------------------------------------------------ index

    PropsIndex index() { return index; }

    Location classLocation(String name) { return classLocations.get(name); }

    private void reindex() {
        PropsIndex idx = new PropsIndex();
        Map<String, Location> locs = new HashMap<>();
        for (String uri : allUris()) {
            if (!uri.endsWith(".jsxj") && !uri.endsWith(".java")) continue;
            String text = text(uri);
            idx.scan(text);
            Matcher m = CLASS.matcher(text);
            while (m.find()) {
                int[] lc = lineCol(text, m.start(1));
                locs.putIfAbsent(m.group(1), new Location(uri, lc[0], lc[1]));
            }
        }
        index = idx;
        classLocations.clear();
        for (Map.Entry<String, Location> e : locs.entrySet()) {
            if (idx.find(e.getKey()) != null) classLocations.put(e.getKey(), e.getValue());
        }
    }

    // ------------------------------------------------------------ javac inputs

    /** Source roots implied by the {@code package} lines of the .java files, plus the configured extra roots. */
    List<Path> javaSourceRoots() {
        Set<Path> found = new HashSet<>(extraSourceRoots);
        for (String uri : allUris()) {
            if (!uri.endsWith(".java")) continue;
            Path p = path(uri);
            Matcher m = PACKAGE.matcher(text(uri));
            if (p == null || p.getParent() == null) continue;
            Path root = p.getParent();
            if (m.find()) {
                for (int i = m.group(1).split("\\.").length; i > 0 && root != null; i--) root = root.getParent();
            }
            if (root != null) found.add(root);
        }
        return new ArrayList<>(found);
    }

    static String packageOf(String text) {
        Matcher m = PACKAGE.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    // ------------------------------------------------------------ helpers

    static Path path(String uri) {
        try { return Path.of(URI.create(uri)); } catch (RuntimeException e) { return null; }
    }

    static int[] lineCol(String text, int offset) {
        int line = 0, last = -1;
        for (int i = 0; i < offset && i < text.length(); i++) if (text.charAt(i) == '\n') { line++; last = i; }
        return new int[]{line, offset - last - 1};
    }

    static int offsetOf(String text, int line, int col) {
        int off = 0;
        for (int l = 0; l < line; l++) {
            int nl = text.indexOf('\n', off);
            if (nl < 0) return text.length();
            off = nl + 1;
        }
        return Math.min(off + col, text.length());
    }
}

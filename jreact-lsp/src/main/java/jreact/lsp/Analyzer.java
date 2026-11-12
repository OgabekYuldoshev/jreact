package jreact.lsp;

import jreact.compiler.CompileError;
import jreact.compiler.JsxTranspiler;
import jreact.compiler.PropsIndex;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Diagnostics of a .jrx file in two steps, like the real build: the JSX parser of jrxc (exact position), then
 * javac on the generated Java. Line N of the generated source is line N of the .jrx file, so javac errors map
 * back by line; on lines without JSX the column is exact too.
 */
final class Analyzer {

    /** Zero-based range, LSP severity (1 error, 2 warning, 3 info). */
    record Diag(int line, int col, int endLine, int endCol, int severity, String message, String source) {}

    /** Immutable inputs, so javac can run off the server lock. */
    record Input(String uri, Map<String, String> jrx, PropsIndex index, List<Path> javaRoots, List<String> classpath) {}

    private Analyzer() {}

    /** Fast part: only the jrxc parser. Null when the file has no JSX errors. */
    static Diag parse(String uri, String text, PropsIndex index) {
        try {
            new JsxTranspiler(name(uri), text, index).transpile();
            return null;
        } catch (CompileError e) {
            int line = Math.max(0, e.line() - 1), col = Math.max(0, e.col() - 1);
            String[] lines = text.split("\n", -1);
            String l = line < lines.length ? lines[line] : "";
            int end = col;
            while (end < l.length() && (Character.isLetterOrDigit(l.charAt(end)) || l.charAt(end) == '_')) end++;
            if (end == col) end = Math.min(l.length(), col + 1);
            return new Diag(line, col, line, Math.max(end, col + 1), 1, e.getMessage(), "jrxc");
        } catch (RuntimeException e) {
            return new Diag(0, 0, 0, 1, 1, "jrxc failed: " + e, "jrxc");
        }
    }

    /** Slow part: javac on the generated Java of {@code in.uri()}. Empty when javac is unavailable. */
    static List<Diag> javac(Input in, Path scratch) {
        List<Diag> out = new ArrayList<>();
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        String text = in.jrx().get(in.uri());
        if (jc == null || text == null) return out;
        String generated;
        try {
            generated = new JsxTranspiler(name(in.uri()), text, in.index()).transpile();
        } catch (RuntimeException e) {
            return out;                                   // reported by parse()
        }
        try {
            Path gen = scratch.resolve("gen"), classes = scratch.resolve("classes");
            deleteTree(gen);
            Files.createDirectories(gen);
            Files.createDirectories(classes);
            // the other .jrx files, as Java, so components defined in them resolve
            for (Map.Entry<String, String> e : in.jrx().entrySet()) {
                if (e.getKey().equals(in.uri())) continue;
                try {
                    String other = new JsxTranspiler(name(e.getKey()), e.getValue(), in.index()).transpile();
                    Path target = gen.resolve(relativeJavaPath(e.getKey(), e.getValue()));
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, other, StandardCharsets.UTF_8);
                } catch (RuntimeException ignored) {}
            }
            List<String> sp = new ArrayList<>();
            sp.add(gen.toString());
            for (Path r : in.javaRoots()) sp.add(r.toString());
            List<String> opts = new ArrayList<>(List.of("-proc:none", "-implicit:none", "-Xlint:none", "-encoding", "UTF-8",
                    "-d", classes.toString(), "-sourcepath", String.join(File.pathSeparator, sp)));
            if (!in.classpath().isEmpty()) opts.addAll(List.of("-classpath", String.join(File.pathSeparator, in.classpath())));

            String rel = relativeJavaPath(in.uri(), text);
            JavaFileObject src = new SimpleJavaFileObject(URI.create("string:///" + rel.replace('\\', '/')), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignore) { return generated; }
            };
            DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
            try (StandardJavaFileManager fm = jc.getStandardFileManager(dc, Locale.ENGLISH, StandardCharsets.UTF_8)) {
                jc.getTask(null, fm, dc, opts, null, List.of(src)).call();
            }
            String[] genLines = generated.split("\n", -1), srcLines = text.split("\n", -1);
            for (Diagnostic<? extends JavaFileObject> d : dc.getDiagnostics()) {
                if (d.getSource() != src || d.getLineNumber() < 1) continue;
                int line = (int) d.getLineNumber() - 1;
                int severity = d.getKind() == Diagnostic.Kind.ERROR ? 1
                        : d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING ? 2 : 3;
                String l = line < srcLines.length ? srcLines[line] : "";
                int col, endCol;
                boolean sameLine = line < genLines.length && genLines[line].equals(l);
                if (sameLine && d.getColumnNumber() > 0) {
                    col = (int) d.getColumnNumber() - 1;
                    endCol = d.getEndPosition() > d.getStartPosition() ? col + (int) (d.getEndPosition() - d.getStartPosition()) : col + 1;
                    endCol = Math.min(endCol, Math.max(l.length(), col + 1));
                } else {                                  // the line had JSX: columns moved, mark the code on the line
                    col = indent(l);
                    endCol = Math.max(l.stripTrailing().length(), col + 1);
                }
                out.add(new Diag(line, col, line, endCol, severity, d.getMessage(Locale.ENGLISH), "javac"));
            }
        } catch (IOException | RuntimeException e) {
            // diagnostics are best effort
        }
        return out;
    }

    /** {@code app/Card.java} for a file with {@code package app;} and name Card.jrx. */
    static String relativeJavaPath(String uri, String text) {
        String pkg = Workspace.packageOf(text);
        String file = name(uri).replaceAll("\\.jrx$", ".java");
        return pkg.isEmpty() ? file : pkg.replace('.', '/') + "/" + file;
    }

    static String name(String uri) {
        int slash = uri.lastIndexOf('/');
        String n = slash >= 0 ? uri.substring(slash + 1) : uri;
        return java.net.URLDecoder.decode(n, StandardCharsets.UTF_8);
    }

    private static int indent(String l) {
        int i = 0;
        while (i < l.length() && Character.isWhitespace(l.charAt(i))) i++;
        return i;
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> w = Files.walk(dir)) {
            for (Path p : w.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.deleteIfExists(p);
        }
    }
}

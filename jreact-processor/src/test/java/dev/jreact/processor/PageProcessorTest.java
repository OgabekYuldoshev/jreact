package dev.jreact.processor;

import jreact.router.RouteObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageProcessorTest {

    record Result(List<String> errors, ClassLoader loader) {}

    static Result compile(Path dir, Map<String, String> sources) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, code) -> units.add(new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return code; }
        }));
        Path out = Files.createDirectories(dir.resolve("out"));
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, null, null)) {
            fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(out.toFile()));
            javac.getTask(null, fm, diagnostics, List.of("-classpath", System.getProperty("java.class.path"), "--release", "17", "-processor", "dev.jreact.processor.PageProcessor"), null, units).call();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR) errors.add(d.getMessage(null));
            return new Result(errors, new URLClassLoader(new URL[]{out.toUri().toURL()}, PageProcessorTest.class.getClassLoader()));
        }
    }

    /** A component class with an empty Props, optionally with annotation and members. */
    static String component(String pkg, String annotation, String name, String members) {
        return "package " + pkg + ";\nimport dev.jreact.api.*;\nimport jreact.*;\nimport jreact.router.*;\n"
                + annotation + "\npublic class " + name + " extends Component<" + name + ".Props> {\n"
                + "    public record Props() {}\n    public Element render(Props p) { return Jsx.el(\"p\", null, null, \"" + name + "\"); }\n" + members + "}\n";
    }

    static final String LOADER = "    public static Object loader(DataArgs a) { return 1; }\n";
    static final String ACTION = "    public static Object action(DataArgs a) { return 2; }\n";

    @SuppressWarnings("unchecked")
    static String tree(ClassLoader loader) throws Exception {
        List<RouteObject> routes = (List<RouteObject>) loader.loadClass("dev.jreact.generated.Pages").getMethod("routes").invoke(null);
        StringBuilder b = new StringBuilder();
        for (RouteObject r : routes) dump(r, 0, b);
        return b.toString();
    }

    static void dump(RouteObject r, int depth, StringBuilder b) {
        b.append("  ".repeat(depth)).append(r.index() ? "(index)" : r.path() == null ? "(boundary)" : r.path()).append(' ').append(r.id() == null ? "" : r.id());
        if (r.loader() != null) b.append(" loader");
        if (r.action() != null) b.append(" action");
        if (r.errorElement() != null) b.append(" error");
        b.append('\n');
        for (RouteObject c : r.children()) dump(c, depth + 1, b);
    }

    @Test
    void layoutsNestPagesByPathPrefix(@TempDir Path dir) throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("app.AppLayout", component("app", "@Layout(value = \"/\", error = app.Oops.class)", "AppLayout", ""));
        src.put("app.Oops", component("app", "", "Oops", ""));
        src.put("app.Home", component("app", "@Page(\"/\")", "Home", ""));
        src.put("app.BookList", component("app", "@Page(\"/books\")", "BookList", LOADER));
        src.put("app.BookNew", component("app", "@Page(\"/books/new\")", "BookNew", ACTION));
        src.put("app.BookDetail", component("app", "@Page(\"/books/{id}\")", "BookDetail", LOADER));
        src.put("app.BookEdit", component("app", "@Page(\"/books/:id/edit\")", "BookEdit", LOADER + ACTION));
        src.put("app.AdminLayout", component("app", "@Layout(\"/admin\")", "AdminLayout", LOADER));
        src.put("app.Dashboard", component("app", "@Page(value = \"/admin\", error = app.Oops.class)", "Dashboard", ""));
        src.put("app.Users", component("app", "@Page(\"/admin/users\")", "Users", ""));
        src.put("app.NotFound", component("app", "@Page(\"/*\")", "NotFound", ""));
        Result r = compile(dir, src);
        assertEquals(List.of(), r.errors());
        // generated in path order; the router ranks by specificity, so the order does not decide what matches
        assertEquals("""
                / AppLayout
                  (boundary)  error
                    (index) Home
                    * NotFound
                    admin AdminLayout loader
                      (index) Dashboard error
                      users Users
                    books BookList loader
                    books/:id BookDetail loader
                    books/:id/edit BookEdit loader action
                    books/new BookNew action
                """, tree(r.loader()));
    }

    @Test
    void withoutARootLayoutPagesAreTopLevelAndPagesAppIsReady(@TempDir Path dir) throws Exception {
        Result r = compile(dir, Map.of(
                "app.Home", component("app", "@Page(\"/\")", "Home", ""),
                "app.About", component("app", "@Page(\"/about\")", "About", ""),
                "app.Docs", component("app", "@Layout(\"/docs\")", "Docs", ""),
                "app.DocPage", component("app", "@Page(\"/docs/:slug\")", "DocPage", "")));
        assertEquals(List.of(), r.errors());
        assertEquals("(index) Home\nabout About\ndocs Docs\n  :slug DocPage\n", tree(r.loader()));
        Object app = r.loader().loadClass("dev.jreact.generated.PagesApp").getDeclaredConstructor().newInstance();
        assertTrue(app instanceof dev.jreact.api.ClientApp);
        assertEquals(4, ((dev.jreact.api.ClientApp) app).routes().size() + 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aStaticTitleWrapsThePageInAHeadScope(@TempDir Path dir) throws Exception {
        Result r = compile(dir, Map.of(
                "app.Home", component("app", "@Page(value = \"/\", title = \"Home \\\"quoted\\\" \\\\ page\")", "Home", ""),
                "app.About", component("app", "@Page(\"/about\")", "About", "")));
        assertEquals(List.of(), r.errors());
        List<RouteObject> routes = (List<RouteObject>) r.loader().loadClass("dev.jreact.generated.Pages").getMethod("routes").invoke(null);
        RouteObject home = routes.stream().filter(x -> "Home".equals(x.id())).findFirst().orElseThrow();
        RouteObject about = routes.stream().filter(x -> "About".equals(x.id())).findFirst().orElseThrow();
        assertEquals(jreact.HeadScope.class, home.element().type());
        jreact.HeadScope.Props props = (jreact.HeadScope.Props) home.element().props();
        assertEquals("Home \"quoted\" \\ page", props.head().title());
        assertEquals("app.Home", props.child().type().getName());
        assertEquals("app.About", about.element().type().getName());            // no title: no wrapper
    }

    @Test
    void mistakesAreCompileErrorsOnTheClass(@TempDir Path dir) throws Exception {
        assertTrue(first(dir, Map.of(
                "app.BookDetail", component("app", "@Page(\"/books/:id\")", "BookDetail", ""),
                "app.BookView", component("app", "@Page(\"/books/{bookId}\")", "BookView", ""))).contains("(the path may differ only in parameter names)"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Layout(\"/x\")", "A", ""), "app.B", component("app", "@Layout(\"/x\")", "B", ""))).startsWith("two layouts for /x"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"books\")", "A", ""))).contains("must start with /"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a/*/b\")", "A", ""))).contains("can only be the last segment"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a/\")", "A", ""))).contains("must not end with /"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a\")", "A", "    public static Object loader(String s) { return 1; }\n"))).contains("must be `public static Object loader(DataArgs a)`"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a\")", "A", "    static Object loader(DataArgs a) { return 1; }\n"))).contains("must be `public static Object loader(DataArgs a)`"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a\")", "A", "").replace("public record Props() {}", "public record Props(String x) {}"))).contains("must have an empty `record Props()`"));
        assertTrue(first(dir, Map.of("app.A", "package app;\nimport dev.jreact.api.*;\n@Page(\"/a\")\npublic class A {}\n")).contains("must extend jreact.Component<Props>"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Layout(\"/a/*\")", "A", ""))).contains("a layout path cannot end with *"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a\")", "A", ""), "other.A", component("other", "@Page(\"/b\")", "A", ""))).contains("two classes are called A"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(\"/a\")", "A", "").replace("public class", "class"))).contains("must be public"));
        assertTrue(first(dir, Map.of("app.A", component("app", "@Page(value = \"/a\", error = app.Bad.class)", "A", ""), "app.Bad", "package app;\npublic class Bad {}\n")).contains("must extend jreact.Component<Props>"));
    }

    static String first(Path dir, Map<String, String> src) throws Exception {
        Path sub = Files.createTempDirectory(dir, "case");
        List<String> errors = compile(sub, src).errors();
        assertTrue(!errors.isEmpty(), "expected a compile error");
        return errors.get(0);
    }
}

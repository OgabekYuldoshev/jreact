package jreact.lsp;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Drives the real server over piped streams with real JSON-RPC framing (run: ./build.sh test, from the repo root). */
public final class LspTest {

    static final String CARD = """
            package app;

            import java.util.List;
            import jreact.Component;
            import jreact.Element;

            public class Card extends Component<Card.Props> {
                public record Props(String title, boolean open, List<Object> children) {}

                @Override
                public Element render(Props props) {
                    return <section className="card"><h2>{props.title()}</h2>{props.children()}</section>;
                }
            }
            """;

    static final String PAGE_OK = """
            package app;

            import jreact.Component;
            import jreact.Element;

            public class Page extends Component<Page.Props> {
                public record Props() {}

                @Override
                public Element render(Props props) {
                    int n = 1;
                    return <Card title="Hi" open={true}><p>{n}</p></Card>;
                }
            }
            """;

    static final String PAGE_JSX_ERROR = PAGE_OK.replace("<p>{n}</p></Card>", "<p>{n}</Card>");
    static final String PAGE_JAVA_ERROR = PAGE_OK.replace("int n = 1;", "int n = \"one\";");
    static final String PAGE_BAD_PROP = PAGE_OK.replace("open={true}", "open={true} colour=\"red\"");

    // ------------------------------------------------------------ client side

    static PipedOutputStream toServer;
    static final BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
    static final List<Map<String, Object>> seen = new ArrayList<>();
    static long nextId = 1;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("jrx-lsp-test");
        Path dir = root.resolve("src/main/jrx/app");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Card.jrx"), CARD);
        Files.writeString(dir.resolve("Page.jrx"), PAGE_OK);
        // a test fixture with the same class name must not shadow the real component
        Files.createDirectories(root.resolve("src/test/java/app"));
        Files.writeString(root.resolve("src/test/java/app/Card.java"),
                "package app;\npublic class Card { public record Props(int unrelated) {} }\n");
        String core = new File("jreact-core/src/main/java").getAbsolutePath();

        PipedInputStream serverIn = new PipedInputStream(1 << 20);
        toServer = new PipedOutputStream(serverIn);
        PipedInputStream fromServer = new PipedInputStream(1 << 20);
        PipedOutputStream serverOut = new PipedOutputStream(fromServer);
        Server server = new Server(serverIn, serverOut);
        int[] exitCode = {-1};
        Thread st = new Thread(() -> exitCode[0] = server.run());
        st.setDaemon(true);
        st.start();
        Thread rt = new Thread(() -> {
            try {
                while (true) {
                    String body = readFrame(fromServer);
                    if (body == null) return;
                    inbox.add((Map<String, Object>) Json.parse(body));
                }
            } catch (IOException e) { /* closed */ }
        });
        rt.setDaemon(true);
        rt.start();

        String cardUri = dir.resolve("Card.jrx").toUri().toString();
        String pageUri = dir.resolve("Page.jrx").toUri().toString();

        test("initialize advertises the features", () -> {
            Map<String, Object> init = new LinkedHashMap<>();
            init.put("rootUri", root.toUri().toString());
            init.put("capabilities", Map.of());
            init.put("initializationOptions", Map.of("sourceRoots", List.of(core)));
            Map<String, Object> res = request("initialize", init);
            Map<String, Object> caps = (Map<String, Object>) ((Map<String, Object>) res.get("result")).get("capabilities");
            check(Boolean.TRUE.equals(caps.get("hoverProvider")), "hover");
            check(Boolean.TRUE.equals(caps.get("definitionProvider")), "definition");
            check(caps.get("completionProvider") != null, "completion");
            notify("initialized", Map.of());
        });

        test("a correct file has no diagnostics", () -> {
            open(pageUri, PAGE_OK);
            List<Map<String, Object>> d = diagnostics(pageUri);
            check(d.isEmpty(), "expected none, got " + d);
        });

        test("JSX syntax error is reported at once, at the right line", () -> {
            change(pageUri, PAGE_JSX_ERROR);
            List<Map<String, Object>> d = diagnostics(pageUri);
            check(d.size() == 1, "one diagnostic: " + d);
            eq("jrxc", d.get(0).get("source"));
            eq(1L, d.get(0).get("severity"));
            eq(11, line(d.get(0)));         // the `return <Card ...>` line, zero-based
        });

        test("javac error on a Java line is reported with an exact range", () -> {
            change(pageUri, PAGE_JAVA_ERROR);
            List<Map<String, Object>> d = diagnostics(pageUri);
            check(!d.isEmpty(), "javac diagnostic expected");
            Map<String, Object> first = d.get(0);
            eq("javac", first.get("source"));
            eq(10, line(first));
            check(String.valueOf(first.get("message")).contains("String cannot be converted to int"), String.valueOf(first.get("message")));
            Map<String, Object> start = (Map<String, Object>) ((Map<String, Object>) first.get("range")).get("start");
            eq(16L, start.get("character"));   // `        int n = "one";`: the literal starts at column 16
        });

        test("unknown prop is reported by the compiler", () -> {
            change(pageUri, PAGE_BAD_PROP);
            List<Map<String, Object>> d = diagnostics(pageUri);
            check(d.size() == 1 && String.valueOf(d.get(0).get("message")).contains("no prop 'colour'"), String.valueOf(d));
            check(String.valueOf(d.get(0).get("message")).contains("title, open, children"), "lists the props: " + d);
        });

        test("fixing the file clears the diagnostics", () -> {
            change(pageUri, PAGE_OK);
            check(diagnostics(pageUri).isEmpty(), "cleared");
        });

        test("completion: component names after <", () -> {
            String text = PAGE_OK.replace("<Card title", "<Ca title");
            change(pageUri, text);
            List<String> labels = complete(pageUri, text, "<Ca");
            check(labels.contains("Card") && labels.contains("Page") && labels.contains("div"), labels.toString());
            check(labels.indexOf("Card") < labels.indexOf("div"), "components sort before html tags");
        });

        test("completion: props of a component, without the ones already given", () -> {
            String text = PAGE_OK.replace("<Card title=\"Hi\" open={true}>", "<Card title=\"Hi\" ><x>");
            change(pageUri, text);
            List<String> labels = complete(pageUri, text, "<Card title=\"Hi\" ");
            check(labels.contains("open") && labels.contains("key"), labels.toString());
            check(!labels.contains("title"), "already given: " + labels);
            check(!labels.contains("children"), "children are nested, not an attribute: " + labels);
        });

        test("completion: html attributes, nothing inside {expressions} or strings", () -> {
            String text = PAGE_OK.replace("<Card title=\"Hi\" open={true}>", "<div cla");
            change(pageUri, text);
            check(complete(pageUri, text, "<div cla").contains("className"), "className");
            String inExpr = PAGE_OK.replace("{n}", "{n.");
            change(pageUri, inExpr);
            check(complete(pageUri, inExpr, "{n.").isEmpty(), "inside an expression");
            String inString = PAGE_OK.replace("title=\"Hi\"", "title=\"Hi ");
            change(pageUri, inString);
            check(complete(pageUri, inString, "title=\"Hi ").isEmpty(), "inside a string");
            String generics = PAGE_OK.replace("int n = 1;", "java.util.List<String> xs = null; java.util.List<St");
            change(pageUri, generics);
            check(complete(pageUri, generics, "java.util.List<St").isEmpty(), "generics are not tags");
        });

        test("hover on a tag and on a prop shows the Props signature and type", () -> {
            change(pageUri, PAGE_OK);
            Map<String, Object> h = at("textDocument/hover", pageUri, PAGE_OK, "<Card", 2);
            String md = (String) ((Map<String, Object>) ((Map<String, Object>) h.get("result")).get("contents")).get("value");
            check(md.contains("record Props(String title, boolean open, List<Object> children)"), md);
            Map<String, Object> h2 = at("textDocument/hover", pageUri, PAGE_OK, "open={true}", 1);
            String md2 = (String) ((Map<String, Object>) ((Map<String, Object>) h2.get("result")).get("contents")).get("value");
            check(md2.contains("boolean open"), md2);
            check(at("textDocument/hover", pageUri, PAGE_OK, "int n", 1).get("result") == null, "no hover on plain Java");
        });

        test("go to definition of a component tag", () -> {
            Map<String, Object> r = at("textDocument/definition", pageUri, PAGE_OK, "<Card", 2);
            Map<String, Object> loc = (Map<String, Object>) r.get("result");
            eq(cardUri, loc.get("uri"));
            Map<String, Object> start = (Map<String, Object>) ((Map<String, Object>) loc.get("range")).get("start");
            eq(6, ((Number) start.get("line")).intValue());   // `public class Card` is the 7th line
        });

        test("unknown requests get an error, shutdown + exit end the server", () -> {
            Map<String, Object> r = request("custom/nothing", Map.of());
            check(r.get("error") != null, "error response");
            request("shutdown", null);
            notify("exit", Map.of());
            st.join(5000);
            eq(0, exitCode[0]);
        });

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> diagnostics(String uri) throws Exception {
        Map<String, Object> m = await(x -> "textDocument/publishDiagnostics".equals(x.get("method"))
                && uri.equals(((Map<String, Object>) x.get("params")).get("uri")));
        return (List<Map<String, Object>>) ((Map<String, Object>) m.get("params")).get("diagnostics");
    }

    @SuppressWarnings("unchecked")
    static List<String> complete(String uri, String text, String before) throws Exception {
        Map<String, Object> r = at("textDocument/completion", uri, text, before, before.length());
        List<String> labels = new ArrayList<>();
        for (Object o : (List<Object>) r.get("result")) labels.add((String) ((Map<String, Object>) o).get("label"));
        return labels;
    }

    /** Position request at (end of the first occurrence of {@code marker}) minus {@code back}... see below. */
    static Map<String, Object> at(String method, String uri, String text, String marker, int advance) throws Exception {
        int idx = text.indexOf(marker);
        if (idx < 0) throw new AssertionError("marker not found: " + marker);
        int off = method.equals("textDocument/completion") ? idx + advance : idx + advance;
        int[] lc = Workspace.lineCol(text, off);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of("line", lc[0], "character", lc[1]));
        return request(method, params);
    }

    static void open(String uri, String text) throws IOException {
        notify("textDocument/didOpen", Map.of("textDocument", Map.of("uri", uri, "languageId", "jrx", "version", 1, "text", text)));
    }

    static void change(String uri, String text) throws IOException {
        notify("textDocument/didChange", Map.of("textDocument", Map.of("uri", uri, "version", 2),
                "contentChanges", List.of(Map.of("text", text))));
    }

    static int line(Map<String, Object> diag) {
        @SuppressWarnings("unchecked")
        Map<String, Object> start = (Map<String, Object>) ((Map<String, Object>) diag.get("range")).get("start");
        return ((Number) start.get("line")).intValue();
    }

    static Map<String, Object> request(String method, Object params) throws Exception {
        long id = nextId++;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("method", method);
        m.put("params", params);
        write(m);
        return await(x -> x.containsKey("id") && !x.containsKey("method") && ((Number) x.get("id")).longValue() == id);
    }

    static void notify(String method, Object params) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("method", method);
        m.put("params", params);
        write(m);
    }

    static void write(Map<String, Object> m) throws IOException {
        byte[] body = Json.write(m).getBytes(StandardCharsets.UTF_8);
        toServer.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        toServer.write(body);
        toServer.flush();
    }

    /** First message matching {@code p}; earlier non-matching ones (stale diagnostics of other steps) are dropped. */
    static Map<String, Object> await(Predicate<Map<String, Object>> p) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            Map<String, Object> m = inbox.poll(Math.max(1, (deadline - System.nanoTime()) / 1_000_000), TimeUnit.MILLISECONDS);
            if (m == null) throw new AssertionError("timeout waiting for a message");
            if (p.test(m)) return m;
        }
    }

    static String readFrame(java.io.InputStream in) throws IOException {
        int length = -1;
        while (true) {
            StringBuilder line = new StringBuilder();
            int b;
            while ((b = in.read()) != -1 && b != '\n') if (b != '\r') line.append((char) b);
            if (b == -1) return null;
            if (line.length() == 0) break;
            if (line.toString().toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    interface Body { void run() throws Exception; }

    static void test(String name, Body body) {
        try { body.run(); passed++; System.out.println("ok   " + name); }
        catch (Throwable t) { failures.add(name + ": " + t); t.printStackTrace(System.out); }
    }

    static void eq(Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError("expected: " + expected + "\n  actual: " + actual);
        }
    }

    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}

package jreact.lsp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Language Server Protocol over a pair of streams (JSON-RPC with Content-Length framing). Features: diagnostics
 * (jrxc parse errors at once, javac on the generated Java shortly after typing stops), completion of component
 * names and props, hover and go-to-definition for tags. Messages are handled one at a time; javac runs on a
 * separate thread and its results are dropped if the file changed meanwhile.
 */
public final class Server {

    private static final long JAVAC_DEBOUNCE_MS = 500;

    private final InputStream in;
    private final OutputStream out;
    private final Object lock = new Object();          // guards workspace and versions
    private final Workspace ws = new Workspace();
    private final Map<String, Integer> versions = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jrx-javac");
        t.setDaemon(true);
        return t;
    });
    private Path scratch;
    private boolean shutdown;

    public Server(InputStream in, OutputStream out) { this.in = in; this.out = out; }

    /** Runs until {@code exit} or end of input. Returns the process exit code. */
    public int run() {
        try {
            while (true) {
                String body = readMessage();
                if (body == null) break;
                @SuppressWarnings("unchecked") Map<String, Object> msg = (Map<String, Object>) Json.parse(body);
                if (!handle(msg)) break;
            }
        } catch (IOException e) {
            System.err.println("jrx-lsp: " + e);
        } finally {
            worker.shutdownNow();
            try { if (scratch != null) Analyzer.deleteTree(scratch); } catch (IOException ignored) {}
        }
        return shutdown ? 0 : 1;
    }

    // ------------------------------------------------------------ dispatch

    @SuppressWarnings("unchecked")
    private boolean handle(Map<String, Object> msg) throws IOException {
        String method = (String) msg.get("method");
        Object id = msg.get("id");
        Map<String, Object> params = (Map<String, Object>) msg.getOrDefault("params", Map.of());
        if (method == null) return true;                      // a response to us, nothing to do
        try {
            switch (method) {
                case "initialize" -> respond(id, initialize(params));
                case "initialized" -> { synchronized (lock) { ws.scan(); } }
                case "shutdown" -> { shutdown = true; respond(id, null); }
                case "exit" -> { return false; }
                case "textDocument/didOpen" -> {
                    Map<String, Object> d = (Map<String, Object>) params.get("textDocument");
                    String uri = (String) d.get("uri");
                    synchronized (lock) { ws.open(uri, (String) d.get("text")); bump(uri); }
                    analyze(uri, 0);
                }
                case "textDocument/didChange" -> {
                    String uri = (String) ((Map<String, Object>) params.get("textDocument")).get("uri");
                    List<Object> changes = (List<Object>) params.get("contentChanges");
                    String text = (String) ((Map<String, Object>) changes.get(changes.size() - 1)).get("text");
                    synchronized (lock) { ws.change(uri, text); bump(uri); }
                    analyze(uri, JAVAC_DEBOUNCE_MS);
                }
                case "textDocument/didSave" -> {
                    List<String> uris;
                    synchronized (lock) { uris = ws.openUris(); }
                    for (String u : uris) scheduleJavac(u, 0);
                }
                case "textDocument/didClose" -> {
                    String uri = (String) ((Map<String, Object>) params.get("textDocument")).get("uri");
                    synchronized (lock) { ws.close(uri); versions.remove(uri); cancel(uri); }
                    publish(uri, List.of());
                }
                case "textDocument/completion" -> respond(id, withDoc(params, (text, off) -> Features.completion(ws, text, off), List.of()));
                case "textDocument/hover" -> respond(id, withDoc(params, (text, off) -> Features.hover(ws, text, off), null));
                case "textDocument/definition" -> respond(id, withDoc(params, (text, off) -> Features.definition(ws, text, off), null));
                default -> { if (id != null) respondError(id, -32601, "method not found: " + method); }
            }
        } catch (RuntimeException e) {
            System.err.println("jrx-lsp: error in " + method + ": " + e);
            if (id != null) respondError(id, -32603, String.valueOf(e));
        }
        return true;
    }

    private interface DocQuery { Object run(String text, int offset); }

    @SuppressWarnings("unchecked")
    private Object withDoc(Map<String, Object> params, DocQuery q, Object none) {
        String uri = (String) ((Map<String, Object>) params.get("textDocument")).get("uri");
        Map<String, Object> pos = (Map<String, Object>) params.get("position");
        synchronized (lock) {
            String text = ws.text(uri);
            if (text == null) return none;
            int off = Workspace.offsetOf(text, ((Number) pos.get("line")).intValue(), ((Number) pos.get("character")).intValue());
            Object r = q.run(text, off);
            return r == null ? none : r;
        }
    }

    @SuppressWarnings("unchecked")
    private Object initialize(Map<String, Object> params) throws IOException {
        synchronized (lock) {
            if (params.get("rootUri") instanceof String u) addRoot(u);
            if (params.get("workspaceFolders") instanceof List<?> fs) {
                for (Object f : fs) addRoot((String) ((Map<String, Object>) f).get("uri"));
            }
            Object opts = params.get("initializationOptions");
            if (opts instanceof Map<?, ?> m) {
                Path base = firstRoot();
                if (m.get("classpath") instanceof List<?> cp) for (Object c : cp) ws.classpath.add(String.valueOf(c));
                if (m.get("sourceRoots") instanceof List<?> sr) {
                    for (Object s : sr) {
                        Path p = Path.of(String.valueOf(s));
                        ws.addSourceRoot(p.isAbsolute() || base == null ? p : base.resolve(p));
                    }
                }
            }
        }
        scratch = Files.createTempDirectory("jrx-lsp");
        Map<String, Object> save = new LinkedHashMap<>();
        save.put("includeText", false);
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("openClose", true);
        sync.put("change", 1);
        sync.put("save", save);
        Map<String, Object> completion = new LinkedHashMap<>();
        completion.put("triggerCharacters", List.of("<"));
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("textDocumentSync", sync);
        caps.put("completionProvider", completion);
        caps.put("hoverProvider", true);
        caps.put("definitionProvider", true);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "jrx-lsp");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilities", caps);
        result.put("serverInfo", info);
        return result;
    }

    private Path root0;

    private void addRoot(String uri) {
        Path p = Workspace.path(uri);
        if (p == null) return;
        if (root0 == null) root0 = p;
        ws.addRoot(p);
    }

    private Path firstRoot() { return root0; }

    // ------------------------------------------------------------ diagnostics

    private void bump(String uri) { versions.merge(uri, 1, Integer::sum); }

    /** Parse errors right away, javac after {@code delayMs} (debounced). */
    private void analyze(String uri, long delayMs) throws IOException {
        Analyzer.Diag parseError;
        synchronized (lock) {
            String text = ws.text(uri);
            parseError = text == null || !uri.endsWith(".jrx") ? null : Analyzer.parse(uri, text, ws.index());
        }
        if (parseError != null) {
            synchronized (lock) { cancel(uri); }
            publish(uri, List.of(parseError));
        } else {
            scheduleJavac(uri, delayMs);
        }
    }

    private void cancel(String uri) {
        ScheduledFuture<?> f = pending.remove(uri);
        if (f != null) f.cancel(false);
    }

    private void scheduleJavac(String uri, long delayMs) {
        synchronized (lock) {
            cancel(uri);
            if (!uri.endsWith(".jrx") || worker.isShutdown()) return;
            pending.put(uri, worker.schedule(() -> runJavac(uri), delayMs, TimeUnit.MILLISECONDS));
        }
    }

    private void runJavac(String uri) {
        Analyzer.Input input;
        int version;
        synchronized (lock) {
            String text = ws.text(uri);
            if (text == null || !ws.isOpen(uri)) return;
            Analyzer.Diag parseError = Analyzer.parse(uri, text, ws.index());
            if (parseError != null) return;               // reported by analyze()
            version = versions.getOrDefault(uri, 0);
            input = new Analyzer.Input(uri, ws.jrxFiles(), ws.index(), ws.javaSourceRoots(), new ArrayList<>(ws.classpath));
        }
        List<Analyzer.Diag> diags = Analyzer.javac(input, scratch);
        synchronized (lock) {
            if (versions.getOrDefault(uri, 0) != version || !ws.isOpen(uri)) return;   // edited meanwhile
        }
        try { publish(uri, diags); } catch (IOException e) { System.err.println("jrx-lsp: " + e); }
    }

    private void publish(String uri, List<Analyzer.Diag> diags) throws IOException {
        List<Object> list = new ArrayList<>();
        for (Analyzer.Diag d : diags) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("range", Features.range(d.line(), d.col(), d.endLine(), d.endCol()));
            m.put("severity", d.severity());
            m.put("source", d.source());
            m.put("message", d.message());
            list.add(m);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uri", uri);
        params.put("diagnostics", list);
        notify("textDocument/publishDiagnostics", params);
    }

    // ------------------------------------------------------------ wire format

    private void respond(Object id, Object result) throws IOException {
        if (id == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("result", result);
        send(m);
    }

    private void respondError(Object id, int code, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("error", err);
        send(m);
    }

    private void notify(String method, Object params) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("method", method);
        m.put("params", params);
        send(m);
    }

    private void send(Map<String, Object> msg) throws IOException {
        byte[] body = Json.write(msg).getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        }
    }

    /** One framed message body, or null at end of input. */
    private String readMessage() throws IOException {
        int length = -1;
        while (true) {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1 && b != '\n') if (b != '\r') line.write(b);
            if (b == -1 && line.size() == 0) return null;
            String h = line.toString(StandardCharsets.US_ASCII);
            if (h.isEmpty()) break;
            if (h.regionMatches(true, 0, "Content-Length:", 0, 15)) length = Integer.parseInt(h.substring(15).trim());
        }
        if (length < 0) return null;
        byte[] body = in.readNBytes(length);
        return new String(body, StandardCharsets.UTF_8);
    }
}

package dev.jreact.spring;

import dev.jreact.api.ClientApp;
import dev.jreact.api.Rpc;
import jreact.Element;
import jreact.Json;
import jreact.Jsx;
import jreact.ServerRenderer;
import jreact.router.DataRouter;
import jreact.router.ErrorResponse;
import jreact.router.Location;
import jreact.router.Promise;
import jreact.router.RouterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server side rendering of the first page: runs the app's routes on a memory router for the requested URL (the loaders call the
 * {@code @ServerApi} beans in-process), renders the matched page to HTML and puts it, together with the answers the loaders got,
 * into index.html. The browser then hydrates that HTML instead of building the page again, and its loaders get the embedded answers
 * instead of calling the server a second time.
 * <p>
 * Falls back to the plain index.html (the app then renders in the browser as usual) when a loader is asynchronous (it waits for something
 * the JVM has no event loop for) or when anything fails.
 */
final class SsrRenderer {

    private static final Logger log = LoggerFactory.getLogger(SsrRenderer.class);

    /** What to answer: a page, or a redirect. */
    record Page(int status, String html, String redirect) {}

    private final ClientApp app;
    private final IndexHtml index;
    private final Pattern container;

    /** The answers of the loaders of the request being rendered, per thread. */
    private final ThreadLocal<Map<String, Object>> recording = new ThreadLocal<>();

    SsrRenderer(String appClass, String containerId, RpcController rpc, IndexHtml index, ClassLoader loader) {
        try {
            this.app = (ClientApp) loader.loadClass(appClass).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("jreact.ssr.app=" + appClass + " must be a public class with a public no-argument constructor that implements dev.jreact.api.ClientApp. "
                    + "Is the client module's jar (with its classes) on the server's classpath? " + e, e);
        }
        this.index = index;
        this.container = Pattern.compile("(<[a-zA-Z][^>]*\\bid=[\"']" + Pattern.quote(containerId) + "[\"'][^>]*>)(\\s*)(</[a-zA-Z]+>)");
        Rpc.transport((api, method, args) -> {
            RpcController.Result r = rpc.invoke(api, method, args);
            Object value = Json.parse(r.json());
            if (!r.ok()) {
                String message = value instanceof Map<?, ?> m && m.get("message") instanceof String s ? s : "error";
                org.springframework.http.HttpStatus known = org.springframework.http.HttpStatus.resolve(r.status());
                Map<String, String> fields = new java.util.LinkedHashMap<>();
                if (value instanceof Map<?, ?> m && m.get("fields") instanceof Map<?, ?> f) for (Map.Entry<?, ?> e : f.entrySet()) fields.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                return Promise.rejected(new ErrorResponse(r.status(), known == null ? "Error" : known.getReasonPhrase(), message, fields));
            }
            Map<String, Object> rec = recording.get();
            if (rec != null) rec.put(Rpc.key(api, method, args), value);
            return Promise.resolved(value);
        });
    }

    /** The page for {@code uri} (path and query, without the context path), or null when the browser has to do it. */
    Page render(String uri, String contextPath) {
        Map<String, Object> answers = new LinkedHashMap<>();
        recording.set(answers);
        DataRouter router = null;
        try {
            // links on the page carry the context path, like the browser's router does with its basename
            router = new DataRouter(app.routes(), new jreact.router.MemoryHistory(contextPath + uri), contextPath.isEmpty() ? null : contextPath);
            DataRouter.Snapshot s = router.state();
            if (!s.initialized() || s.matches() == null) return null;       // a loader waits for something: not on the JVM
            Location wanted = Location.parse(uri);
            Location now = s.location();
            if (!now.pathname().equals(wanted.pathname()) || !now.search().equals(wanted.search())) {
                return new Page(302, null, now.pathname() + now.search());
            }
            Element root = Jsx.c(RouterProvider.class, RouterProvider::new, null, new RouterProvider.Props(router, app.fallback()));
            String body = ServerRenderer.renderToString(root);
            int status = 200;
            for (Object error : s.errors().values()) {
                status = error instanceof ErrorResponse r ? r.status() : 500;
                break;
            }
            return new Page(status, assemble(body, answers, contextPath), null);
        } catch (RuntimeException | IOException e) {
            log.warn("jreact ssr: {} could not be rendered on the server, the browser will render it ({})", uri, e.toString());
            return null;
        } finally {
            recording.remove();
            if (router != null) router.dispose();
        }
    }

    private String assemble(String body, Map<String, Object> answers, String contextPath) throws IOException {
        String template = index.html(contextPath);
        Matcher m = container.matcher(template);
        if (!m.find()) throw new IOException("index.html has no empty element with the id of jreact.ssr.container-id");
        String data = answers.isEmpty() ? "" : "<script id=\"__jreact_data\" type=\"application/json\">" + Json.stringify(answers) + "</script>";
        return template.substring(0, m.start()) + m.group(1) + body + m.group(3) + data + template.substring(m.end());
    }
}

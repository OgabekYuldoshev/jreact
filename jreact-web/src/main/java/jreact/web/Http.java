package jreact.web;

import jreact.Json;
import jreact.router.ErrorResponse;
import jreact.router.Promise;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Calls the backend: {@code fetch} with the page's cookies, JSON in and out, and the CSRF token that Spring Security keeps in the
 * {@code XSRF-TOKEN} cookie sent back as {@code X-XSRF-TOKEN} on every request that changes data.
 * <pre>{@code
 * Http.json("GET", "/api/todos", null)                       // Promise<Object>: List / Map / String / Long ... see jreact.Json
 *     .then(list -> ...);
 * Http.json("POST", "/api/todos", Map.of("title", "milk"));  // a non-2xx answer rejects the promise with an ErrorResponse
 * }</pre>
 * Meant to be used from loaders and actions: a rejected promise lands on the route's {@code errorElement}, and a 404 from the server is
 * the same {@code ErrorResponse(404)} a loader would throw itself.
 */
public final class Http {
    private Http() {}

    /** What the server answered. {@link #json()} parses the body. */
    public record Response(int status, String statusText, String body) {
        public boolean ok() { return status >= 200 && status < 300; }

        public Object json() { return body == null || body.isEmpty() ? null : Json.parse(body); }
    }

    @JSFunctor
    private interface Done extends JSObject {
        void call(int status, String statusText, String body, String error);
    }

    /**
     * The path the app is served under ({@code /shop} when Spring Boot runs with {@code server.servlet.context-path=/shop}), or "".
     * The server writes it into index.html as {@code <meta name="jreact-base" content="/shop">}. Requests to a URL that starts with
     * {@code /} get it as a prefix, and {@code Routers.createBrowserRouter} uses it as the router's basename.
     */
    public static String base() {
        String b = meta();
        if (b == null || b.equals("/")) return "";
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    /** When the server named a login page ({@code jreact.login-url}) a 401 navigates there, with {@code ?next=} the current page. Does nothing on the login page itself. */
    private static void goToLogin() {
        String login = meta("jreact-login");
        if (login != null && !login.isEmpty()) redirect(login.startsWith("/") ? base() + login : login);
    }

    @JSBody(params = {"login"}, script = "var path = login.split('?')[0]; if (location.pathname === path) return; location.assign(login + (login.indexOf('?') < 0 ? '?' : '&') + 'next=' + encodeURIComponent(location.pathname + location.search));")
    private static native void redirect(String login);

    @JSBody(params = {"name"}, script = "var m = document.querySelector('meta[name=\"' + name + '\"]'); return m ? m.getAttribute('content') : null;")
    private static native String meta(String name);

    @JSBody(script = "var m = document.querySelector('meta[name=\"jreact-base\"]'); return m ? m.getAttribute('content') : null;")
    private static native String meta();

    /** Sends a request. Resolves for every HTTP status; rejects only when the network fails. {@code body}: a String is sent as is, anything else as JSON. */
    public static Promise<Response> request(String method, String url, Object body) {
        Promise<Response> out = Promise.pending();
        if (url.startsWith("/") && !url.startsWith("//")) url = base() + url;
        String payload = body == null ? null : body instanceof String s ? s : Json.stringify(body);
        String type = body == null ? null : body instanceof String ? "text/plain;charset=UTF-8" : "application/json";
        try {
            send(method, url, payload, type, (status, text, data, error) -> {
                if (error != null) out.reject(new ErrorResponse(0, "Network Error", error));
                else out.resolve(new Response(status, text, data));
            });
        } catch (RuntimeException e) {
            out.reject(e);
        }
        return out;
    }

    /** JSON in, JSON out. A non-2xx status rejects with {@code ErrorResponse(status, statusText, body)}; the data is the parsed JSON error body when there is one. */
    public static Promise<Object> json(String method, String url, Object body) {
        return request(method, url, body).then(r -> {
            if (!r.ok()) {
                Object data;
                try { data = r.json(); } catch (RuntimeException notJson) { data = r.body(); }
                if (r.status() == 401) goToLogin();            // jreact.login-url: sign in, then come back
                throw new ErrorResponse(r.status(), r.statusText(), data);
            }
            return r.json();
        });
    }

    public static Promise<Object> get(String url) { return json("GET", url, null); }

    public static Promise<Object> post(String url, Object body) { return json("POST", url, body); }

    public static Promise<Object> put(String url, Object body) { return json("PUT", url, body); }

    public static Promise<Object> patch(String url, Object body) { return json("PATCH", url, body); }

    public static Promise<Object> delete(String url) { return json("DELETE", url, null); }

    @JSBody(params = {"method", "url", "body", "type", "done"}, script =
            "var headers = {'Accept': 'application/json'};"
            + "if (type) headers['Content-Type'] = type;"
            + "if (method !== 'GET' && method !== 'HEAD') {"
            + "  var m = /(?:^|; )XSRF-TOKEN=([^;]*)/.exec(document.cookie);"
            + "  if (m) headers['X-XSRF-TOKEN'] = decodeURIComponent(m[1]);"
            + "}"
            + "fetch(url, {method: method, headers: headers, body: body === null ? undefined : body, credentials: 'same-origin'})"
            + ".then(function(r) { return r.text().then(function(t) { done(r.status, r.statusText || '', t, null); }); })"
            + ".catch(function(e) { done(0, '', null, String(e && e.message || e)); });")
    private static native void send(String method, String url, String body, String type, Done done);
}

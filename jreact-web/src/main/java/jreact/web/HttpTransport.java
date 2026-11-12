package jreact.web;

import dev.jreact.api.Rpc;
import jreact.router.ErrorResponse;
import jreact.router.Promise;

import java.util.List;
import java.util.Map;

/**
 * The browser end of {@code @ServerApi}: {@code POST /_rpc/<Api>/<method>} with the arguments as a JSON array. Installed by
 * {@code JReactWeb.createRoot}. A non-2xx answer rejects with {@code ErrorResponse(status, text, message)}, the message being the one
 * the server wrote ({@code RpcException}, validation, ...), so a route's errorElement can show it.
 */
public final class HttpTransport implements Rpc.Transport {
    private final String prefix;

    public HttpTransport(String prefix) { this.prefix = prefix.endsWith("/") ? prefix : prefix + "/"; }

    public HttpTransport() { this("/_rpc"); }

    @Override
    public Promise<Object> call(String api, String method, List<Object> args) {
        return Http.json("POST", prefix + api + "/" + method, args).recover(error -> {
            if (error instanceof ErrorResponse r && r.data() instanceof Map<?, ?> m && m.get("message") instanceof String text) {
                throw new ErrorResponse(r.status(), r.statusText(), text);
            }
            if (error instanceof RuntimeException e) throw e;
            throw new IllegalStateException(error);
        });
    }
}

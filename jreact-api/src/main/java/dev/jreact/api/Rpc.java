package dev.jreact.api;

import jreact.router.Promise;

import java.util.List;

/** How a generated client reaches the server. jreact-web installs the HTTP transport; tests can install a fake one. */
public final class Rpc {
    private Rpc() {}

    @FunctionalInterface
    public interface Transport {
        /** Sends one call; {@code args} is a JSON array (as Lists/Maps/Strings/Numbers). Resolves with the JSON value of the result. */
        Promise<Object> call(String api, String method, List<Object> args);
    }

    private static Transport transport;

    public static void transport(Transport t) { transport = t; }

    public static Transport transport() { return transport; }

    // answers the server already gave while rendering the page (embedded in the HTML), used once each by the first matching calls
    private static final java.util.Map<String, Object> preloaded = new java.util.HashMap<>();

    /** The key under which a call and its answer are exchanged between the server's render and the browser's hydration. */
    public static String key(String api, String method, List<Object> args) { return api + "/" + method + "/" + jreact.Json.stringify(args); }

    /** Browser: answers embedded in the page; the next call with the same arguments gets one instead of going over the network. */
    public static void preload(java.util.Map<String, Object> answers) { preloaded.putAll(answers); }

    public static Promise<Object> call(String api, String method, List<Object> args) {
        if (!preloaded.isEmpty()) {
            String key = key(api, method, args);
            if (preloaded.containsKey(key)) return Promise.resolved(preloaded.remove(key));
        }
        if (transport == null) return Promise.rejected(new IllegalStateException("no RPC transport: start the app with JReactWeb.createRoot(..) or call Rpc.transport(..)"));
        return transport.call(api, method, args);
    }
}

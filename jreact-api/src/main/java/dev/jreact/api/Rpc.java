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

    public static Promise<Object> call(String api, String method, List<Object> args) {
        if (transport == null) return Promise.rejected(new IllegalStateException("no RPC transport: start the app with JReactWeb.createRoot(..) or call Rpc.transport(..)"));
        return transport.call(api, method, args);
    }
}

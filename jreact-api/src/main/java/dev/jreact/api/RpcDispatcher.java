package dev.jreact.api;

import java.util.List;

/** Generated per {@link ServerApi} interface: calls the method named in a request with arguments parsed from JSON. Server side only. */
public interface RpcDispatcher {

    /** The URL segment: the simple name of the interface. */
    String name();

    /** The {@link ServerApi} interface. */
    Class<?> api();

    /** Calls {@code method} on {@code impl}; {@code args} is the parsed JSON array. Returns the JSON value of the result (null for void). */
    Object invoke(Object impl, String method, List<Object> args);
}

package dev.jreact.api;

import java.util.Map;

/**
 * Throw from a server method to answer with an HTTP status: {@code throw new RpcException(404, "no todo 7")}. The browser gets an
 * {@code ErrorResponse(status, ..., message)} and the route's errorElement shows it. Any other exception is a 500 with a generic
 * message (details stay in the server log).
 * <p>For a refused form use status 422 and, when you know them, the messages per field:
 * {@code throw new RpcException(422, "The form has errors.", Map.of("title", "must not be blank"))}. The browser's
 * {@code ErrorResponse.fields()} has them. Bean Validation ({@code @Valid}, {@code @NotBlank}...) produces the same thing without code.
 */
public class RpcException extends RuntimeException {
    private final int status;
    private final Map<String, String> fields;

    public RpcException(int status, String message) { this(status, message, Map.of()); }

    public RpcException(int status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public int status() { return status; }

    /** Messages per field name (the record component or parameter name), empty when there are none. */
    public Map<String, String> fields() { return fields; }
}

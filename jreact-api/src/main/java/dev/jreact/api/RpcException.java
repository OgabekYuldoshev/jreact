package dev.jreact.api;

/**
 * Throw from a server method to answer with an HTTP status: {@code throw new RpcException(404, "no todo 7")}. The browser gets an
 * {@code ErrorResponse(status, ..., message)} and the route's errorElement shows it. Any other exception is a 500 with a generic
 * message (details stay in the server log).
 */
public class RpcException extends RuntimeException {
    private final int status;

    public RpcException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}

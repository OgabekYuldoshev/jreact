package jreact.router;

/**
 * An error with an HTTP-like status, for "not found" and "forbidden" pages: {@code throw new ErrorResponse(404, "Not Found", "no such contact")}.
 * It shows up in {@code useRouteError()}; test it with {@link #isErrorResponse}. The router itself uses status 404 when no route matches
 * and 405 when a form is submitted to a route without an action.
 */
public final class ErrorResponse extends RuntimeException {
    private final int status;
    private final String statusText;
    private final transient Object data;

    public ErrorResponse(int status, String statusText, Object data) {
        super(status + " " + statusText, null, false, false);
        this.status = status;
        this.statusText = statusText;
        this.data = data;
    }

    public ErrorResponse(int status, String statusText) { this(status, statusText, null); }

    public int status() { return status; }

    public String statusText() { return statusText; }

    /** Whatever was given as {@code data}, or null. */
    public Object data() { return data; }

    /** True when the thing from {@code useRouteError()} is an ErrorResponse (and not an exception thrown by code). */
    public static boolean isErrorResponse(Object error) { return error instanceof ErrorResponse; }
}

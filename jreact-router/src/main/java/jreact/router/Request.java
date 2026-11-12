package jreact.router;

/**
 * What a loader or action is asked to serve: the method ({@code GET} for loads), the URL (without basename) and, for a
 * form submission, the URL-encoded fields.
 */
public record Request(String method, Location url, String body) {

    /** The query string of the URL. */
    public SearchParams searchParams() { return SearchParams.parse(url.search()); }

    /** The submitted form fields (empty for a GET). */
    public SearchParams form() { return SearchParams.parse(body == null ? "" : body); }
}

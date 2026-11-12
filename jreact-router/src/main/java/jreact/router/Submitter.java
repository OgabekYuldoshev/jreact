package jreact.router;

import java.util.Map;

/** What {@code useSubmit()} returns: sends data to a route's action like a form would. */
public interface Submitter {

    /** How to submit: {@code method} (default POST), {@code action} (default: the current route), {@code replace} the history entry. */
    record Options(String method, String action, boolean replace) {
        public static final Options DEFAULT = new Options(null, null, false);
    }

    /** Submits URL-encoded fields ({@code name=Ada&done=true}). */
    void submit(String urlEncodedFields, Options options);

    default void submit(Map<String, String> fields) { submit(fields, Options.DEFAULT); }

    default void submit(Map<String, String> fields, Options options) {
        SearchParams p = SearchParams.empty();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            p = e.getValue() == null || e.getValue().isEmpty() ? p : p.with(e.getKey(), e.getValue());
        }
        submit(p.toString(), options);
    }
}

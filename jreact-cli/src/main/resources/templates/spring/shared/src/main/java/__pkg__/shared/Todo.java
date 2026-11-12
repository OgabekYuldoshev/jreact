package {{package}}.shared;

import java.util.Map;

/**
 * A todo, as the server sends it. Both sides use this class: the server (Jackson writes the record as
 * {@code {"id":1,"title":"...","done":false}}) and the browser, which reads the parsed JSON with {@link #fromJson}.
 */
public record Todo(long id, String title, boolean done) {

    public static Todo fromJson(Object json) {
        Map<?, ?> m = (Map<?, ?>) json;
        return new Todo(((Number) m.get("id")).longValue(), String.valueOf(m.get("title")), Boolean.TRUE.equals(m.get("done")));
    }
}

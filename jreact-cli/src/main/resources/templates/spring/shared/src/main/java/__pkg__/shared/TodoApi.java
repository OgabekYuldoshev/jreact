package {{package}}.shared;

import dev.jreact.api.ServerApi;

import java.util.List;

/**
 * What the browser can ask of the server. Write the interface once:
 * <ul>
 *   <li>the server implements it (see TodoService in server/): a normal Spring bean</li>
 *   <li>the browser calls it through the generated {@code TodoApiClient}: {@code TodoApiClient.add("milk")} returns a {@code Promise<Todo>}</li>
 * </ul>
 * JSON, URLs ({@code POST /_rpc/TodoApi/add}) and type checks are generated. A signature that cannot cross the wire is a compile error.
 * Every method is a public endpoint: check permissions on the server (for example {@code @PreAuthorize} on the bean).
 */
@ServerApi
public interface TodoApi {
    List<Todo> list();

    Todo add(String title);

    Todo setDone(long id, boolean done);

    void remove(long id);
}

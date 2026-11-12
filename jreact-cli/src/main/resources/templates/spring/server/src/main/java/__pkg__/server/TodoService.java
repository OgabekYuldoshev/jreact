package {{package}}.server;

import dev.jreact.api.RpcException;
import {{package}}.shared.Todo;
import {{package}}.shared.TodoApi;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The server side of {@link TodoApi}: an ordinary Spring bean, so transactions, @PreAuthorize and injected repositories work as usual.
 * In memory to keep the template small: swap the list for a repository.
 * Throw {@link RpcException} to answer with a status; any other exception is a 500 that does not leak its message.
 */
@Service
class TodoService implements TodoApi {

    private final List<Todo> todos = new CopyOnWriteArrayList<>(List.of(
            new Todo(1, "Read the README", true),
            new Todo(2, "Change something in client/ and watch the page reload", false),
            new Todo(3, "Change something in server/", false)));
    private final AtomicLong ids = new AtomicLong(3);

    @Override
    public List<Todo> list() { return todos; }

    @Override
    public Todo add(String title) {
        String clean = title == null ? "" : title.strip();
        if (clean.isEmpty() || clean.length() > 200) throw new RpcException(422, "title must be 1 to 200 characters");
        Todo todo = new Todo(ids.incrementAndGet(), clean, false);
        todos.add(todo);
        return todo;
    }

    @Override
    public Todo setDone(long id, boolean done) {
        for (int i = 0; i < todos.size(); i++) {
            Todo t = todos.get(i);
            if (t.id() != id) continue;
            Todo changed = new Todo(t.id(), t.title(), done);
            todos.set(i, changed);
            return changed;
        }
        throw new RpcException(404, "no todo " + id);
    }

    @Override
    public void remove(long id) {
        if (!todos.removeIf(t -> t.id() == id)) throw new RpcException(404, "no todo " + id);
    }
}

package {{package}}.server;

import {{package}}.shared.Todo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** The API the page talks to. In memory, to keep the template small: swap the list for a repository. */
@RestController
@RequestMapping("/api/todos")
class TodoController {

    private final List<Todo> todos = new CopyOnWriteArrayList<>(List.of(
            new Todo(1, "Read the README", true),
            new Todo(2, "Change something in client/ and watch the page reload", false),
            new Todo(3, "Change something in server/", false)));
    private final AtomicLong ids = new AtomicLong(3);

    @GetMapping
    List<Todo> list() { return todos; }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    Todo add(@RequestBody Map<String, Object> body) {
        String title = body.get("title") instanceof String s ? s.strip() : "";
        if (title.isEmpty() || title.length() > 200) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "title must be 1 to 200 characters");
        Todo todo = new Todo(ids.incrementAndGet(), title, false);
        todos.add(todo);
        return todo;
    }

    @PatchMapping("/{id}")
    Todo update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        for (int i = 0; i < todos.size(); i++) {
            Todo t = todos.get(i);
            if (t.id() != id) continue;
            Todo changed = new Todo(t.id(), t.title(), Boolean.TRUE.equals(body.get("done")));
            todos.set(i, changed);
            return changed;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no todo " + id);
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        if (!todos.removeIf(t -> t.id() == id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no todo " + id);
    }
}

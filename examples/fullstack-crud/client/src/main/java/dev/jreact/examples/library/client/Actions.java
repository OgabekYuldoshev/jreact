package dev.jreact.examples.library.client;

import dev.jreact.examples.library.shared.*;
import jreact.router.*;

import java.util.Map;
import java.util.Optional;

/** What the pages' loaders and actions share: reading the URL and the form, and the create/edit action. All data comes from BookApiClient (generated from BookApi in shared/). */
final class Actions {
    private Actions() {}

    /** Reads the form, asks the server to create ({@code id == null}) or update, then goes to the book. A refusal (422) stays on the form. */
    static Promise<Object> save(Long id, DataArgs a) {
        SearchParams f = a.request().form();
        BookInput input;
        try {
            input = new BookInput(f.get("title", ""), f.get("author", ""), Integer.parseInt(f.get("year", "").strip()),
                BookStatus.valueOf(f.get("status", "AVAILABLE")), Optional.of(f.get("isbn", "")));
        } catch (RuntimeException notANumber) {
            return Promise.resolved(new FormError("The year must be a number.", Map.of("year", "The year must be a number.")));
        }
        Promise<Book> saved = id == null ? BookApiClient.create(input) : BookApiClient.update(id, input);
        return saved.then(book -> (Object) Redirect.to("/books/" + book.id()))
            .recover(error -> {
                if (error instanceof ErrorResponse r && r.status() == 422) return new FormError(String.valueOf(r.data()), r.fields());
                if (error instanceof RuntimeException e) throw e;
                throw new IllegalStateException(error);
            });
    }

    static long id(DataArgs a) {
        try {
            return Long.parseLong(a.params().get("id"));
        } catch (NumberFormatException e) {
            throw new ErrorResponse(404, "Not Found", "There is no book " + a.params().get("id") + ".");
        }
    }

    static int number(String text, int fallback) {
        try { return text == null ? fallback : Math.max(Integer.parseInt(text), 1); } catch (NumberFormatException e) { return fallback; }
    }

    static Optional<BookStatus> status(String text) {
        if (text == null || text.isEmpty()) return Optional.empty();
        for (BookStatus s : BookStatus.values()) if (s.name().equals(text)) return Optional.of(s);
        return Optional.empty();
    }
}

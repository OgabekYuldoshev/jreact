package dev.jreact.examples.library.shared;

import dev.jreact.api.ServerApi;

import java.util.Map;
import java.util.Optional;

/**
 * The whole backend as seen from the browser. The server implements it as a Spring bean (BookService); the browser calls the generated
 * {@code BookApiClient}, whose methods return {@code Promise<T>}. The same calls run on the server while the first page is rendered.
 */
@ServerApi
public interface BookApi {

    /** Search in title and author, optionally only one status; 8 books per page, {@code page} from 1. */
    BookPage list(String query, Optional<BookStatus> status, int page);

    /** 404 when there is no such book. */
    Book get(long id);

    /** 422 with a message when the input is not valid. */
    Book create(BookInput input);

    Book update(long id, BookInput input);

    void delete(long id);

    /** How many books each status has (all statuses are in the map). */
    Map<String, Long> counts();
}

package dev.jreact.examples.library.server;

import dev.jreact.api.RpcException;
import dev.jreact.examples.library.shared.Book;
import dev.jreact.examples.library.shared.BookApi;
import dev.jreact.examples.library.shared.BookInput;
import dev.jreact.examples.library.shared.BookPage;
import dev.jreact.examples.library.shared.BookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.Year;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The server side of {@link BookApi}: an ordinary Spring bean, so transactions, Spring Data and (with Spring Security) @PreAuthorize work as usual.
 * {@link RpcException} answers with its status and message (the browser shows the message); any other exception is a 500 that does not leak details.
 */
@Service
@Validated                  // Bean Validation: the @Valid BookInput of BookApi is checked before a method runs (422 with a message per field)
@Transactional
class BookService implements BookApi {

    static final int PAGE_SIZE = 8;

    private final BookRepository books;

    BookService(BookRepository books) { this.books = books; }

    @Override
    @Transactional(readOnly = true)
    public BookPage list(String query, Optional<BookStatus> status, int page) {
        String q = query == null ? "" : query.strip().toLowerCase();
        Page<BookEntity> result = books.search(q, status.orElse(null), PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by("title").and(Sort.by("id"))));
        // asking for a page past the end shows the last one instead of an empty list
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            result = books.search(q, status.orElse(null), PageRequest.of(Math.max(result.getTotalPages(), 1) - 1, PAGE_SIZE, Sort.by("title").and(Sort.by("id"))));
        }
        return new BookPage(result.getContent().stream().map(BookService::toBook).toList(), result.getNumber() + 1, Math.max(result.getTotalPages(), 1), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Book get(long id) { return toBook(find(id)); }

    @Override
    public Book create(BookInput input) {
        BookInput in = validated(input);
        return toBook(books.save(new BookEntity(in.title(), in.author(), in.year(), in.status(), in.isbn().orElse(null), LocalDate.now())));
    }

    @Override
    public Book update(long id, BookInput input) {
        BookInput in = validated(input);
        BookEntity entity = find(id);
        entity.title = in.title();
        entity.author = in.author();
        entity.year = in.year();
        entity.status = in.status();
        entity.isbn = in.isbn().orElse(null);
        return toBook(entity);                      // managed entity: saved when the transaction commits
    }

    @Override
    public void delete(long id) { books.delete(find(id)); }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<BookStatus, Long> found = new EnumMap<>(BookStatus.class);
        for (Object[] row : books.countByStatus()) found.put((BookStatus) row[0], (Long) row[1]);
        Map<String, Long> out = new LinkedHashMap<>();          // every status is there, also with 0
        for (BookStatus s : BookStatus.values()) out.put(s.name(), found.getOrDefault(s, 0L));
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private BookEntity find(long id) {
        return books.findById(id).orElseThrow(() -> new RpcException(404, "There is no book " + id + "."));
    }

    /** The rules that annotations cannot say: trimming, a year not in the future, the ISBN shape. Throws a 422 with the field's name. */
    static BookInput validated(BookInput in) {
        int latest = Year.now().getValue() + 1;
        if (in.year() > latest) throw new RpcException(422, "The year must be between 1000 and " + latest + ".", Map.of("year", "The year must be between 1000 and " + latest + "."));
        Optional<String> isbn = in.isbn() == null ? Optional.empty() : in.isbn().map(String::strip).filter(s -> !s.isEmpty());
        if (isbn.isPresent() && !isbn.get().matches("[0-9Xx-]{10,17}")) {
            throw new RpcException(422, "The ISBN has 10 or 13 digits (dashes allowed).", Map.of("isbn", "The ISBN has 10 or 13 digits (dashes allowed)."));
        }
        return new BookInput(in.title().strip(), in.author().strip(), in.year(), in.status(), isbn);
    }

    private static Book toBook(BookEntity e) {
        return new Book(e.id, e.title, e.author, e.year, e.status, Optional.ofNullable(e.isbn), e.addedOn);
    }
}

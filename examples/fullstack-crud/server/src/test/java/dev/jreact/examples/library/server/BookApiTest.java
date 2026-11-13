package dev.jreact.examples.library.server;

import dev.jreact.examples.library.shared.BookStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The API as the browser calls it (POST /_rpc/BookApi/<method>), against the real Spring context and database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookApiTest {

    @Autowired TestRestTemplate http;
    @Autowired BookService service;

    ResponseEntity<String> rpc(String method, String args) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/_rpc/BookApi/" + method, HttpMethod.POST, new HttpEntity<>(args, h), String.class);
    }

    static String input(String title, String author, int year, String status, String isbn) {
        return "{\"title\":\"" + title + "\",\"author\":\"" + author + "\",\"year\":" + year + ",\"status\":\"" + status + "\",\"isbn\":" + (isbn == null ? "null" : "\"" + isbn + "\"") + "}";
    }

    @Test
    void listsASortedPageAndSearches() {
        String first = rpc("list", "[\"\", null, 1]").getBody();
        assertThat(first).contains("\"page\":1").contains("\"items\":[{").contains("\"pages\":3").contains("\"total\":23");
        assertThat(first.indexOf("A Philosophy of Software Design")).isLessThan(first.indexOf("Accelerate"));      // by title

        String found = rpc("list", "[\"FOWLER\", null, 1]").getBody();            // case does not matter, author is searched too
        assertThat(found).contains("Refactoring").contains("Patterns of Enterprise").doesNotContain("Clean Code").contains("\"total\":2");

        String lost = rpc("list", "[\"\", \"LOST\", 1]").getBody();
        assertThat(lost).contains("The Phoenix Project").doesNotContain("Clean Code").contains("\"total\":3");
        assertThat(rpc("list", "[\"\", null, 99]").getBody()).contains("\"page\":3");            // past the end: the last page
    }

    @Test
    void createReadUpdateDelete() {
        ResponseEntity<String> created = rpc("create", "[" + input("  Crud Book  ", "Test Author", 2020, "AVAILABLE", null) + "]");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).contains("\"title\":\"Crud Book\"").contains("\"isbn\":null").contains("\"addedOn\":\"");
        String id = created.getBody().replaceAll(".*\"id\":(\\d+).*", "$1");

        assertThat(rpc("get", "[" + id + "]").getBody()).contains("Crud Book");
        ResponseEntity<String> updated = rpc("update", "[" + id + "," + input("Crud Book 2", "Test Author", 2021, "BORROWED", "978-0-306-40615-7") + "]");
        assertThat(updated.getBody()).contains("\"title\":\"Crud Book 2\"").contains("\"status\":\"BORROWED\"").contains("\"isbn\":\"978-0-306-40615-7\"");
        assertThat(rpc("get", "[" + id + "]").getBody()).contains("Crud Book 2");

        assertThat(rpc("delete", "[" + id + "]").getBody()).isEqualTo("null");
        ResponseEntity<String> gone = rpc("get", "[" + id + "]");
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getBody()).contains("There is no book " + id);
        assertThat(rpc("delete", "[" + id + "]").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidInputIsA422WithAMessageForTheForm() {
        assertThat(rpc("create", "[" + input("", "A", 2000, "AVAILABLE", null) + "]").getBody()).contains("The title must be 1 to 200 characters.");
        assertThat(rpc("create", "[" + input("T", " ", 2000, "AVAILABLE", null) + "]").getBody()).contains("The author must be");
        ResponseEntity<String> year = rpc("create", "[" + input("T", "A", 12, "AVAILABLE", null) + "]");
        assertThat(year.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(year.getBody()).contains("The year must be between 1000 and").contains("\"fields\":{\"year\":");      // the message is also given per field
        ResponseEntity<String> both = rpc("create", "[" + input(" ", "", 12, "AVAILABLE", null) + "]");
        assertThat(both.getBody()).contains("\"title\":\"The title must be").contains("\"author\":\"The author must be").contains("\"year\":");
        assertThat(rpc("create", "[" + input("T", "A", 2000, "AVAILABLE", "not-an-isbn") + "]").getBody()).contains("The ISBN has 10 or 13 digits");
        // a wrong type is a 400 that names the place
        ResponseEntity<String> bad = rpc("create", "[{\"title\":\"T\",\"author\":\"A\",\"year\":\"x\",\"status\":\"AVAILABLE\",\"isbn\":null}]");
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).contains("input.year: expected an integer");
        assertThat(rpc("create", "[" + input("T", "A", 2000, "SHELVED", null) + "]").getBody()).contains("is not one of the allowed values");
    }

    @Test
    void countsHaveEveryStatus() {
        String counts = rpc("counts", "[]").getBody();
        assertThat(counts).contains("\"AVAILABLE\":").contains("\"BORROWED\":").contains("\"LOST\":");
        assertThat(service.counts().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(service.list("", Optional.empty(), 1).total());
        assertThat(service.counts().keySet()).containsExactlyElementsOf(java.util.Arrays.stream(BookStatus.values()).map(Enum::name).toList());
    }

    @Test
    void theFirstPageIsRenderedOnTheServerWithTheBooks() {
        HttpHeaders html = new HttpHeaders();
        html.set("Accept", "text/html");
        var page = http.exchange("/?q=fowler", HttpMethod.GET, new HttpEntity<>(html), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Refactoring").contains("Patterns of Enterprise").doesNotContain("Clean Code").contains("__jreact_data");
        assertThat(page.getBody()).contains("<title>Library</title>");
        var detail = http.exchange("/books/1", HttpMethod.GET, new HttpEntity<>(html), String.class).getBody();          // the title of this book, for the tab and for search engines
        assertThat(detail).containsPattern("<title>[^<]+ · Library</title>").contains("<meta name=\"description\"").contains("<meta property=\"og:title\"");
        var missing = http.exchange("/books/999999", HttpMethod.GET, new HttpEntity<>(html), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("There is no book 999999");
    }
}

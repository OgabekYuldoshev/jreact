package {{package}}.server;

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

import static org.assertj.core.api.Assertions.assertThat;

/** Calls the API the way the browser does: POST /_rpc/TodoApi/<method> with the arguments as a JSON array. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TodoApiTest {

    @Autowired TestRestTemplate http;

    ResponseEntity<String> rpc(String method, String args) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/_rpc/TodoApi/" + method, HttpMethod.POST, new HttpEntity<>(args, h), String.class);
    }

    @Test
    void addAndList() {
        ResponseEntity<String> added = rpc("add", "[\"write tests\"]");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(added.getBody()).contains("\"title\":\"write tests\"").contains("\"done\":false");
        assertThat(rpc("list", "[]").getBody()).contains("write tests");
    }

    @Test
    void setDoneAndRemove() {
        String body = rpc("add", "[\"toggle me\"]").getBody();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");
        assertThat(rpc("setDone", "[" + id + ", true]").getBody()).contains("\"done\":true");
        assertThat(rpc("remove", "[" + id + "]").getBody()).isEqualTo("null");
        assertThat(rpc("list", "[]").getBody()).doesNotContain("toggle me");
        assertThat(rpc("remove", "[999999]").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void datesTravelAsIsoStrings() {
        assertThat(rpc("list", "[]").getBody()).containsPattern("\"created\":\"\\d{4}-\\d{2}-\\d{2}\"");
    }

    @Test
    void validationAndTypeErrors() {
        ResponseEntity<String> empty = rpc("add", "[\"  \"]");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(empty.getBody()).contains("The title must be 1 to 200 characters.").contains("\"fields\":{\"title\":");
        ResponseEntity<String> wrongType = rpc("setDone", "[\"seven\", true]");
        assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongType.getBody()).contains("id: expected an integer");
    }

    @Test
    void theFirstPageIsRenderedOnTheServer() {
        HttpHeaders html = new HttpHeaders();
        html.set("Accept", MediaType.TEXT_HTML_VALUE);
        var page = http.exchange("/", HttpMethod.GET, new HttpEntity<>(html), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        // the page, its data and the built assets arrive together: no JavaScript needed to see the list
        assertThat(page.getBody()).contains("<div id=\"root\"").contains("Read the README").contains("__jreact_data").contains("/assets/app.");
        // a URL no route matches is a 404 page (the errorElement), still with the app around it
        var missing = http.exchange("/some/unknown/route", HttpMethod.GET, new HttpEntity<>(html), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("/assets/app.");
        assertThat(http.exchange("/api/nothing", HttpMethod.GET, new HttpEntity<>(html), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

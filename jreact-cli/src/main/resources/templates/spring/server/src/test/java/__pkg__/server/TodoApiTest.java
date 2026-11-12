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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TodoApiTest {

    @Autowired TestRestTemplate http;

    @Test
    void addAndListTodos() {
        var created = http.postForEntity("/api/todos", Map.of("title", "write tests"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.getForObject("/api/todos", String.class)).contains("write tests");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleAndDeleteByIdWork() {
        Map<String, Object> created = http.postForObject("/api/todos", Map.of("title", "toggle me"), Map.class);
        Object id = created.get("id");
        Map<String, Object> done = http.patchForObject("/api/todos/" + id, Map.of("done", true), Map.class);
        assertThat(done.get("done")).isEqualTo(true);
        http.delete("/api/todos/" + id);
        assertThat(http.getForObject("/api/todos", String.class)).doesNotContain("toggle me");
        assertThat(http.exchange("/api/todos/999999", HttpMethod.DELETE, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anEmptyTitleIsRejected() {
        assertThat(http.postForEntity("/api/todos", Map.of("title", "  "), String.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void theBuiltClientIsServedAtTheRootAndForClientRoutes() {
        HttpHeaders html = new HttpHeaders();
        html.set("Accept", MediaType.TEXT_HTML_VALUE);
        for (String path : new String[]{"/", "/some/client/route"}) {
            var page = http.exchange(path, HttpMethod.GET, new HttpEntity<>(html), String.class);
            assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(page.getBody()).contains("<div id=\"root\"").contains("/assets/app.");
        }
        assertThat(http.exchange("/api/nothing", HttpMethod.GET, new HttpEntity<>(html), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

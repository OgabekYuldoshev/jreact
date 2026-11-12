package dev.jreact.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/** Production mode: files come from the classpath (src/test/resources/jreact). */
@SpringBootTest(classes = JReactStarterTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JReactStarterTest {

    @Configuration
    @EnableAutoConfiguration
    static class App {
        @RestController
        static class Hello {
            @GetMapping("/api/hello") String hello() { return "hi"; }
            @GetMapping("/pricing") String pricing() { return "controller wins"; }
        }
    }

    @Autowired TestRestTemplate http;

    ResponseEntity<String> get(String url, String accept, String... more) {
        HttpHeaders h = new HttpHeaders();
        if (accept != null) h.set("Accept", accept);
        for (int i = 0; i < more.length; i += 2) h.set(more[i], more[i + 1]);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    void rootServesIndexWithTheHashedAssets() {
        ResponseEntity<String> r = get("/", "text/html");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("/assets/app.beef.js").doesNotContain("__jreact");
        assertThat(r.getHeaders().getCacheControl()).contains("no-cache");
    }

    @Test
    void aClientSideRouteGetsIndexHtml() {
        ResponseEntity<String> r = get("/contacts/3/edit", "text/html,application/xhtml+xml");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("<div id=\"root\">");
    }

    @Test
    void anUnknownApiUrlIs404NotTheApp() {
        assertThat(get("/api/nothing", "text/html").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api", "text/html").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aFetchForJsonOnAnUnknownUrlIs404NotHtml() {
        assertThat(get("/contacts/3", "application/json").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMissingFileIs404() {
        assertThat(get("/missing.js", "*/*").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/assets/app.nope.js", "*/*").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void controllersStillWin() {
        assertThat(get("/api/hello", "application/json").getBody()).isEqualTo("hi");
        assertThat(get("/pricing", "text/html").getBody()).isEqualTo("controller wins");
    }

    @Test
    void hashedAssetsAreCachedForever() {
        ResponseEntity<String> r = get("/assets/app.beef.js", "*/*");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getCacheControl()).contains("max-age=31536000").contains("immutable");
        assertThat(r.getBody()).isEqualTo("function main(){}");
    }

    @Test
    void theGzipCopyIsServedWhenTheBrowserAcceptsIt() {
        ResponseEntity<byte[]> r = http.exchange("/assets/app.beef.js", HttpMethod.GET, new HttpEntity<>(headers("Accept-Encoding", "gzip")), byte[].class);
        assertThat(r.getHeaders().getFirst("Content-Encoding")).isEqualTo("gzip");
        assertThat(r.getHeaders().getFirst("Vary")).contains("Accept-Encoding");
    }

    @Test
    void thereAreNoDevEndpointsInProduction() {
        assertThat(get("/__jreact/version", "*/*").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    static HttpHeaders headers(String k, String v) { HttpHeaders h = new HttpHeaders(); h.set(k, v); return h; }
}

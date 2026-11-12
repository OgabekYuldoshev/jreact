package dev.jreact.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Server side rendering: the page comes with its HTML and the answers of its loaders. */
@SpringBootTest(classes = JReactStarterTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jreact.ssr.app=dev.jreact.spring.sample.SampleApp")
class SsrTest {

    @Autowired TestRestTemplate http;

    ResponseEntity<String> get(String url, String accept) {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", accept);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    void thePageIsRenderedWithItsData() {
        ResponseEntity<String> r = get("/", "text/html");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        String body = r.getBody();
        assertThat(body).contains("<div id=\"root\"><main><h1>&lt;Greetings&gt; &amp; more</h1><ul><li>a<!-- --> x<!-- -->1</li><li>b<!-- --> x<!-- -->2</li></ul></main></div>");
        assertThat(body).contains("/assets/app.beef.js");           // the rest of index.html is intact
        // the answers the loader got, script safe, keyed like the browser's calls
        assertThat(body).contains("<script id=\"__jreact_data\" type=\"application/json\">{\"GreetApi/all/[]\":[{\"text\":\"a\",\"times\":1},{\"text\":\"b\",\"times\":2}]}</script>");
        assertThat(r.getHeaders().getCacheControl()).contains("no-cache").contains("private");
    }

    @Test
    void aRedirectingLoaderIsA302() {
        var plain = new org.springframework.web.client.RestTemplate(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(java.net.HttpURLConnection c, String m) throws java.io.IOException { super.prepareConnection(c, m); c.setInstanceFollowRedirects(false); }
        });
        plain.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() { @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; } });
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "text/html");
        ResponseEntity<String> r = plain.exchange(http.getRootUri() + "/old", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(302);
        assertThat(r.getHeaders().getFirst("Location")).isEqualTo("/");
    }

    @Test
    void aLoaderErrorKeepsItsStatus() {
        ResponseEntity<String> r = get("/boom", "text/html");
        assertThat(r.getStatusCode().value()).isEqualTo(418);
        assertThat(r.getBody()).contains("<div id=\"root\">");
    }

    @Test
    void anUnknownRouteIsA404Page() {
        assertThat(get("/nowhere", "text/html").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aLoaderThatWaitsFallsBackToThePlainIndexForTheBrowser() {
        ResponseEntity<String> r = get("/slow", "text/html");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).contains("<div id=\"root\"></div>").doesNotContain("__jreact_data");
    }

    @Test
    void nonPageRequestsAreLeftAlone() {
        assertThat(get("/assets/app.beef.js", "*/*").getBody()).doesNotContain("<div");
        assertThat(get("/api/hello", "text/html").getBody()).isEqualTo("hi");
        assertThat(get("/pricing", "text/html").getBody()).isEqualTo("controller wins");
        assertThat(get("/", "application/json").getBody()).doesNotContain("<main>");
    }
}

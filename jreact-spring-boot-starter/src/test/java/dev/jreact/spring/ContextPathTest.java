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

/** The app under a servlet context path, with server side rendering. */
@SpringBootTest(classes = JReactStarterTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/shop", "jreact.ssr.app=dev.jreact.spring.sample.SampleApp"})
class ContextPathTest {

    @Autowired TestRestTemplate http;

    ResponseEntity<String> get(String url, String accept) {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", accept);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    void thePageKnowsItsContextPath() {
        String body = get("/", "text/html").getBody();
        assertThat(body).contains("<meta name=\"jreact-base\" content=\"/shop\">");
        assertThat(body).contains("src=\"/shop/assets/app.beef.js\"").contains("href=\"/shop/assets/app.cafe.css\"");
        assertThat(body).contains("<main><h1>");                                  // still server rendered
    }

    @Test
    void assetsAndRpcLiveUnderThePathToo() {
        assertThat(get("/assets/app.beef.js", "*/*").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/index.html", "text/html").getBody()).contains("content=\"/shop\"");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(http.exchange("/_rpc/GreetApi/all", HttpMethod.POST, new HttpEntity<>("[]", h), String.class).getBody()).contains("\"text\":\"a\"");
    }

    @Test
    void theRedirectKeepsThePath() {
        var plain = new org.springframework.web.client.RestTemplate(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(java.net.HttpURLConnection c, String m) throws java.io.IOException { super.prepareConnection(c, m); c.setInstanceFollowRedirects(false); }
        });
        plain.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() { @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; } });
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "text/html");
        ResponseEntity<String> r = plain.exchange(http.getRootUri() + "/old", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(302);
        assertThat(r.getHeaders().getFirst("Location")).isEqualTo("/shop/");
    }
}

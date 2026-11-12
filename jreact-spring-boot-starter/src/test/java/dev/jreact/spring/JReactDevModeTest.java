package dev.jreact.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Dev mode: files from a folder, reload endpoints, reload script in the page. */
@SpringBootTest(classes = JReactStarterTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"jreact.dev-dir=${java.io.tmpdir}/jreact-starter-dev"})
class JReactDevModeTest {
    @Autowired TestRestTemplate http;

    @org.junit.jupiter.api.BeforeEach
    void files() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "jreact-starter-dev");
        java.nio.file.Files.createDirectories(dir.resolve("assets"));
        java.nio.file.Files.writeString(dir.resolve("index.html"), "<html><body><div id=\"root\"></div></body></html>");
        java.nio.file.Files.writeString(dir.resolve("assets/app.js"), "var dev=1;");
        java.nio.file.Files.writeString(dir.resolve("manifest.json"), "{}");
    }

    @Test
    void servesFromTheFolderAndAddsTheReloadScript() {
        ResponseEntity<String> r = http.exchange("/some/page", HttpMethod.GET, new HttpEntity<>(JReactStarterTest.headers("Accept", "text/html")), String.class);
        assertThat(r.getBody()).contains("<div id=\"root\">").contains("<script src=\"/__jreact/reload.js\"></script></body>");
        assertThat(http.getForObject("/assets/app.js", String.class)).isEqualTo("var dev=1;");
        assertThat(http.getForEntity("/assets/app.js", String.class).getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void theVersionChangesWhenTheBuildChanges() throws Exception {
        String before = http.getForObject("/__jreact/version", String.class);
        java.nio.file.Path manifest = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "jreact-starter-dev", "manifest.json");
        java.nio.file.Files.writeString(manifest, "{\"changed\":true}");
        assertThat(http.getForObject("/__jreact/version", String.class)).isNotEqualTo(before);
        assertThat(http.getForObject("/__jreact/reload.js", String.class)).contains("location.reload()");
    }
}

package dev.jreact.spring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexHtmlTest {

    static final String PAGE = "<!doctype html><html><head><title>t</title><link rel=\"stylesheet\" href=\"/assets/app.css\"></head>"
            + "<body><a href=\"//cdn.example/x\">cdn</a><img src='/logo.png'><script src=\"/assets/app.js\"></script></body></html>";

    @Test
    void withoutAContextPathNothingChanges() {
        assertThat(IndexHtml.adapt(PAGE, "")).isEqualTo(PAGE);
    }

    @Test
    void rootUrlsGetThePrefixAndTheBrowserIsTold() {
        String out = IndexHtml.adapt(PAGE, "/shop");
        assertThat(out).contains("<head><meta name=\"jreact-base\" content=\"/shop\">");
        assertThat(out).contains("href=\"/shop/assets/app.css\"").contains("src='/shop/logo.png'").contains("src=\"/shop/assets/app.js\"");
        assertThat(out).contains("href=\"//cdn.example/x\"");        // protocol-relative URLs are not ours
    }
}

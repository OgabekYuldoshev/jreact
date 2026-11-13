package dev.jreact.spring;

import dev.jreact.spring.sample.GreetApi;
import dev.jreact.spring.sample.GreetService;
import dev.jreact.spring.sample.Greeting;
import dev.jreact.spring.security.JReactSecurityConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spring Security with JReact: the CSRF cookie and header, 401 for nobody and 403 for somebody who may not, bean methods with @PreAuthorize. */
@SpringBootTest(classes = SecurityTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.autoconfigure.exclude=", "jreact.ssr.app=dev.jreact.spring.sample.SampleApp", "jreact.login-url=/login"})
class SecurityTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class App {
        @Bean
        GreetApi greetApi() {
            return new GreetService() {
                @Override @PreAuthorize("isAuthenticated()") public List<Greeting> all() { return super.all(); }
                @Override @PreAuthorize("hasRole('ADMIN')") public String shout(String word) { return super.shout(word); }
            };
        }

        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http
                    .with(JReactSecurityConfigurer.jreact(), c -> {})
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        UserDetailsService users() {
            var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
            return new InMemoryUserDetailsManager(
                    User.withUsername("ada").password(encoder.encode("pw")).roles("USER").build(),
                    User.withUsername("root").password(encoder.encode("pw")).roles("USER", "ADMIN").build());
        }
    }

    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    /** The CSRF token the way the page gets it: from the cookie of any response. */
    String token() {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "text/html");
        ResponseEntity<String> page = http.exchange("/", HttpMethod.GET, new HttpEntity<>(h), String.class);
        String cookie = String.join(";", page.getHeaders().getOrDefault("Set-Cookie", List.of()));
        assertThat(cookie).contains("XSRF-TOKEN=");
        assertThat(cookie).doesNotContain("HttpOnly");          // the page's script has to read it
        return cookie.replaceAll(".*XSRF-TOKEN=([^;]+).*", "$1");
    }

    ResponseEntity<String> rpc(String call, String args, String csrf, String user) { return rpc(call, args, csrf, csrf, user); }

    /** {@code cookie} and {@code header}: what the browser sends as the XSRF-TOKEN cookie and as the X-XSRF-TOKEN header (null: not at all). */
    ResponseEntity<String> rpc(String call, String args, String cookie, String header, String user) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (header != null) h.set("X-XSRF-TOKEN", header);
        if (cookie != null) h.set("Cookie", "XSRF-TOKEN=" + cookie);
        TestRestTemplate client = user == null ? http : http.withBasicAuth(user, "pw");
        return client.exchange("/_rpc/GreetApi/" + call, HttpMethod.POST, new HttpEntity<>(args, h), String.class);
    }

    @Test
    void theFirstResponseCarriesTheCsrfCookieTheScriptCanRead() {
        assertThat(token()).isNotBlank();
    }

    @Test
    void aCallWithoutTheTokenIsRefusedAndWithItGoesThrough() {
        assertThat(rpc("ping", "[]", null, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String csrf = token();
        assertThat(rpc("ping", "[]", csrf, null, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);          // cookie only: a form on another site cannot add the header
        assertThat(rpc("ping", "[]", csrf, "not-the-cookie", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rpc("ping", "[]", csrf, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void nobodySignedInIs401AndSomebodyWhoMayNotIs403() {
        String csrf = token();
        ResponseEntity<String> anonymous = rpc("all", "[]", csrf, null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).isEqualTo("{\"message\":\"authentication required\"}");
        assertThat(rpc("all", "[]", csrf, "ada").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(rpc("shout", "[\"hey\"]", csrf, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> forbidden = rpc("shout", "[\"hey\"]", csrf, "ada");
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).isEqualTo("{\"message\":\"access denied\"}");
        assertThat(rpc("shout", "[\"hey\"]", csrf, "root").getBody()).isEqualTo("\"HEY\"");
    }

    /** A GET that does not follow redirects: status, Location header and body. */
    record Got(int status, String location, String body) {}

    Got get(String path, String basicUser) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "text/html");
        if (basicUser != null) c.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((basicUser + ":pw").getBytes()));
        int status = c.getResponseCode();
        String body = new String((status < 400 ? c.getInputStream() : c.getErrorStream()).readAllBytes());
        return new Got(status, c.getHeaderField("Location"), body);
    }

    @Test
    void aPageWhoseLoaderNeedsASignedInUserRedirectsToTheLoginPageAndComesBack() throws Exception {
        Got anonymous = get("/", null);                       // the loader calls GreetApiClient.all(), which needs a user
        assertThat(anonymous.status()).isEqualTo(302);
        assertThat(anonymous.location()).isEqualTo("/login?next=%2F");
        Got signedIn = get("/", "ada");                       // the loader runs as the signed in user, in the same thread
        assertThat(signedIn.status()).isEqualTo(200);
        assertThat(signedIn.body()).contains("&lt;Greetings&gt; &amp; more");
    }

    @Test
    void theBrowserIsToldWhereTheLoginPageIs() throws Exception {
        assertThat(get("/index.html", null).body()).contains("<meta name=\"jreact-login\" content=\"/login\">");
    }
}

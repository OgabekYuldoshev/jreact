package dev.jreact.spring;

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

@SpringBootTest(classes = JReactStarterTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RpcTest {

    @Autowired TestRestTemplate http;

    ResponseEntity<String> post(String url, String body, MediaType type) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(type);
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    ResponseEntity<String> rpc(String call, String args) { return post("/_rpc/GreetApi/" + call, args, MediaType.APPLICATION_JSON); }

    @Test
    void callsTheBeanAndAnswersWithJson() {
        ResponseEntity<String> r = rpc("greet", "[\"Ada\", 2]");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo("{\"text\":\"hello Ada\",\"times\":2}");
        assertThat(r.getHeaders().getCacheControl()).contains("no-store");
        assertThat(rpc("all", "[]").getBody()).isEqualTo("[{\"text\":\"a\",\"times\":1},{\"text\":\"b\",\"times\":2}]");
    }

    @Test
    void aVoidMethodAnswersNull() {
        ResponseEntity<String> r = rpc("ping", "[]");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo("null");
    }

    @Test
    void rpcExceptionBecomesItsStatusAndMessage() {
        ResponseEntity<String> r = rpc("greet", "[\"Ada\", 0]");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).isEqualTo("{\"message\":\"times must be at least 1\"}");
    }

    @Test
    void aWrongArgumentIs400WithTheParameterName() {
        ResponseEntity<String> r = rpc("greet", "[5, 2]");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("name: expected a string, got a number");
        assertThat(rpc("greet", "[\"a\"]").getBody()).contains("expected 2 argument(s), got 1");
    }

    @Test
    void badBodiesAre400() {
        assertThat(rpc("greet", "{not json").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> notArray = rpc("greet", "{\"name\":\"x\"}");
        assertThat(notArray.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notArray.getBody()).contains("JSON array");
    }

    @Test
    void unknownApiAndMethodAre404() {
        assertThat(post("/_rpc/NoApi/x", "[]", MediaType.APPLICATION_JSON).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rpc("nothing", "[]").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void onlyPostWithJsonIsAccepted() {
        assertThat(http.getForEntity("/_rpc/GreetApi/all", String.class).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        // a cross-site HTML form can only send these types: refused
        assertThat(post("/_rpc/GreetApi/all", "[]", MediaType.TEXT_PLAIN).getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(post("/_rpc/GreetApi/all", "[]", MediaType.APPLICATION_FORM_URLENCODED).getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void securityAndStatusExceptionsMapAndOthersDoNotLeak() {
        assertThat(rpc("fail", "[\"denied\"]").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rpc("fail", "[\"denied\"]").getBody()).doesNotContain("secret");
        ResponseEntity<String> conflict = rpc("fail", "[\"status\"]");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("already there");
        ResponseEntity<String> boom = rpc("fail", "[\"boom\"]");
        assertThat(boom.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(boom.getBody()).isEqualTo("{\"message\":\"Internal error\"}");
    }
}

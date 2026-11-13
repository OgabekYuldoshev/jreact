package dev.jreact.spring.sample;

import dev.jreact.api.RpcException;

import java.util.List;

@org.springframework.validation.annotation.Validated
public class GreetService implements GreetApi {

    /** Looks like Spring Security's exception: the starter maps it by name to 403. */
    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() { super("secret details"); }
    }

    @Override public Greeting greet(String name, int times) {
        if (times < 1) throw new RpcException(422, "times must be at least 1");
        return new Greeting("hello " + name, times);
    }

    @Override public List<Greeting> all() { return List.of(new Greeting("a", 1), new Greeting("b", 2)); }

    @Override public void ping() {}

    @Override public Signup join(Signup form) { return form; }

    @Override public String shout(String word) { return word.toUpperCase(); }

    @Override public Booking book(String who, java.time.LocalDate day, java.util.Optional<String> note) {
        return new Booking(who, day, note, day.atTime(9, 30), java.time.Instant.parse("2026-11-13T04:30:00Z"));
    }

    @Override public void fail(String how) {
        switch (how) {
            case "denied": throw new AccessDeniedException();
            case "status": throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "already there");
            default: throw new IllegalStateException("database password is hunter2");
        }
    }
}

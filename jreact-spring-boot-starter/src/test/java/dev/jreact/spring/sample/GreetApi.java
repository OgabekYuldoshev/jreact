package dev.jreact.spring.sample;

import dev.jreact.api.ServerApi;

import java.util.List;

@ServerApi
public interface GreetApi {
    Greeting greet(String name, int times);
    List<Greeting> all();
    void fail(String how);
    void ping();
    Signup join(@jakarta.validation.Valid Signup form);
    String shout(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 5) String word);
    Booking book(String who, java.time.LocalDate day, java.util.Optional<String> note);
}

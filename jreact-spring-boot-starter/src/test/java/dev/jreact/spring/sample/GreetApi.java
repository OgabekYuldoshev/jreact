package dev.jreact.spring.sample;

import dev.jreact.api.ServerApi;

import java.util.List;

@ServerApi
public interface GreetApi {
    Greeting greet(String name, int times);
    List<Greeting> all();
    void fail(String how);
    void ping();
}

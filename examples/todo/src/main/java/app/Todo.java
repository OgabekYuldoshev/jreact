package app;

public record Todo(long id, String title, boolean done) {
    public Todo toggled() { return new Todo(id, title, !done); }
}

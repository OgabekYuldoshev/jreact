package app;

/** One card on the board. {@code column}: 0 = To do, 1 = In progress, 2 = Done. */
public record Task(long id, String title, String tag, int column) {
    public Task moved(int delta) { return new Task(id, title, tag, column + delta); }
}

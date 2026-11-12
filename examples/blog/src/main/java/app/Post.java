package app;

/** One blog post. */
public record Post(int id, String title, String tag, String date, int minutes, String excerpt, String body) {}

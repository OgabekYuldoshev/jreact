package app;

/** A row of the orders table. {@code amount} is whole dollars, {@code day} the day of the month. */
public record Order(int id, String customer, String product, int amount, String status, int day) {}

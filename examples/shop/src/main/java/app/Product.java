package app;

/** A catalog item. {@code price} is in cents, {@code rating} is tenths of a star (47 = 4.7). */
public record Product(int id, String name, String category, int price, String emoji, int rating) {}

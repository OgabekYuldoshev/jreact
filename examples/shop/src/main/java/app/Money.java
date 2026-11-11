package app;

/** Formats cents as dollars without String.format (keeps the browser bundle small). */
final class Money {
    private Money() {}

    static String format(int cents) {
        int c = cents % 100;
        return "$" + (cents / 100) + "." + (c < 10 ? "0" : "") + c;
    }

    static String stars(int rating) { return (rating / 10) + "." + (rating % 10); }
}

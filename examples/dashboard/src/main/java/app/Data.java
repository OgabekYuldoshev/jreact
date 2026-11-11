package app;

import java.util.List;

/** Fake but stable data for the dashboard, so screenshots and tests are reproducible. */
final class Data {
    private Data() {}

    static final String[] WEEK_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    static final int[] WEEK = {420, 380, 510, 470, 620, 780, 690};

    static final String[] YEAR_LABELS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final int[] YEAR = {2800, 2650, 3100, 3350, 3200, 3700, 3900, 3650, 4100, 4450, 4300, 4900};

    static String[] labels(String range) { return range.equals("7d") ? WEEK_LABELS : YEAR_LABELS; }

    static int[] series(String range) { return range.equals("7d") ? WEEK : YEAR; }

    static final List<Order> ORDERS = List.of(
        new Order(1042, "Amelia Brooks", "Pro plan", 290, "Paid", 28),
        new Order(1041, "Noah Fischer", "Starter plan", 49, "Paid", 28),
        new Order(1040, "Sofia Moretti", "Team plan", 890, "Pending", 27),
        new Order(1039, "Liam Okafor", "Pro plan", 290, "Paid", 27),
        new Order(1038, "Mia Tanaka", "Add-on: SSO", 120, "Refunded", 26),
        new Order(1037, "Ethan Novak", "Team plan", 890, "Paid", 25),
        new Order(1036, "Zara Haddad", "Starter plan", 49, "Pending", 25),
        new Order(1035, "Lucas Ferreira", "Pro plan", 290, "Paid", 24),
        new Order(1034, "Chloe Dubois", "Enterprise", 2400, "Paid", 23),
        new Order(1033, "Oliver Smith", "Add-on: SSO", 120, "Paid", 22),
        new Order(1032, "Hana Kim", "Starter plan", 49, "Refunded", 21),
        new Order(1031, "Diego Alvarez", "Team plan", 890, "Pending", 20)
    );
}

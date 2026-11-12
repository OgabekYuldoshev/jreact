package dev.jreact.cli;

/** Terminal output: a few colors when stdout is a terminal. */
final class Ui {
    private Ui() {}

    private static final boolean COLOR = System.console() != null && System.getenv("NO_COLOR") == null;

    static String green(String s) { return COLOR ? "\u001b[32m" + s + "\u001b[0m" : s; }
    static String red(String s) { return COLOR ? "\u001b[31m" + s + "\u001b[0m" : s; }
    static String yellow(String s) { return COLOR ? "\u001b[33m" + s + "\u001b[0m" : s; }
    static String dim(String s) { return COLOR ? "\u001b[2m" + s + "\u001b[0m" : s; }
    static String bold(String s) { return COLOR ? "\u001b[1m" + s + "\u001b[0m" : s; }

    static void step(String s) { System.out.println(bold("» " + s)); }
    static void ok(String s) { System.out.println(green("✓ ") + s); }
    static void warn(String s) { System.out.println(yellow("! ") + s); }
    static void fail(String s) { System.err.println(red("✗ ") + s); }
    static void info(String s) { System.out.println(s); }
}

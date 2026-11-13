package jreact;

/**
 * Collects the {@link Head} of a page while it is rendered on the server (components render parent first, so the deepest call
 * is merged last and wins). Not thread-safe by design: one render per thread, like the server side renderer itself.
 */
public final class HeadCollector {
    private HeadCollector() {}

    private static final ThreadLocal<Head[]> CURRENT = new ThreadLocal<>();

    /** Starts collecting; call {@link #end()} in a finally block. */
    public static void begin() { CURRENT.set(new Head[] { Head.EMPTY }); }

    /** Stops collecting and returns everything merged. */
    public static Head end() {
        Head[] box = CURRENT.get();
        CURRENT.remove();
        return box == null ? Head.EMPTY : box[0];
    }

    public static boolean collecting() { return CURRENT.get() != null; }

    static void add(Head head) {
        Head[] box = CURRENT.get();
        if (box != null && head != null) box[0] = box[0].merge(head);
    }
}

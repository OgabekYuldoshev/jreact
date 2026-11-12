package app;

import jreact.router.Promise;
import jreact.web.JsTimers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** The "server": contacts in memory. Reads can be slow on purpose (see {@link #later}) so the pending UI is visible. */
public final class Contacts {
    private Contacts() {}

    private static final List<Contact> ALL = new ArrayList<>(List.of(
        new Contact(1, "Ada", "Lovelace", "Wrote the first program for a machine that did not exist yet.", true),
        new Contact(2, "Grace", "Hopper", "Found the first computer bug. It was a moth.", false),
        new Contact(3, "Alan", "Turing", "Asked whether machines can think, then built the test.", false),
        new Contact(4, "Margaret", "Hamilton", "Led the software that landed Apollo 11.", true),
        new Contact(5, "Linus", "Torvalds", "Writes kernels, then a version control system over a weekend.", false)));
    private static int nextId = 6;
    private static final JsTimers TIMERS = new JsTimers();

    /** Delivers {@code value} after {@code ms}: a promise a loader can return, like a network request. */
    public static <T> Promise<Object> later(long ms, Supplier<T> value) {
        Promise<Object> p = Promise.pending();
        TIMERS.setTimeout(() -> { try { p.resolve(value.get()); } catch (RuntimeException e) { p.reject(e); } }, ms);
        return p;
    }

    public static List<Contact> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Contact> out = new ArrayList<>();
        for (Contact c : ALL) if (q.isEmpty() || c.fullName().toLowerCase(Locale.ROOT).contains(q)) out.add(c);
        return out;
    }

    public static Contact find(String id) {
        for (Contact c : ALL) if (String.valueOf(c.id()).equals(id)) return c;
        return null;
    }

    public static Contact create() {
        Contact c = new Contact(nextId++, "", "", "", false);
        ALL.add(0, c);
        return c;
    }

    public static void update(String id, UnaryOperator<Contact> change) {
        for (int i = 0; i < ALL.size(); i++) if (String.valueOf(ALL.get(i).id()).equals(id)) ALL.set(i, change.apply(ALL.get(i)));
    }

    public static void delete(String id) { ALL.removeIf(c -> String.valueOf(c.id()).equals(id)); }
}

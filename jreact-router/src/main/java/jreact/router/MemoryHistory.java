package jreact.router;

import java.util.ArrayList;
import java.util.List;

/** A history that lives in memory: for tests and for rendering a page on the JVM. */
public final class MemoryHistory implements History {

    private final List<Location> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private int index;

    public MemoryHistory() { this("/"); }

    public MemoryHistory(String initial) { entries.add(Location.parse(initial)); }

    @Override public Location location() { return entries.get(index); }

    @Override
    public void push(String to) {
        while (entries.size() > index + 1) entries.remove(entries.size() - 1);   // going forward drops the old forward entries
        entries.add(Location.parse(to));
        index++;
        changed();
    }

    @Override
    public void replace(String to) {
        entries.set(index, Location.parse(to));
        changed();
    }

    @Override
    public void go(int delta) {
        int next = Math.max(0, Math.min(entries.size() - 1, index + delta));
        if (next == index) return;
        index = next;
        changed();
    }

    @Override
    public Runnable listen(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void changed() { for (Runnable l : new ArrayList<>(listeners)) l.run(); }
}

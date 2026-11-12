package jreact.router;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells a loader or action that its result is no longer wanted (the user went somewhere else). Cancel timers or
 * requests in {@link #onAbort}; returning a result anyway is harmless, the router ignores it.
 */
public final class AbortSignal {
    private boolean aborted;
    private final List<Runnable> listeners = new ArrayList<>();

    public boolean aborted() { return aborted; }

    /** Runs {@code listener} when aborted (right away if it already is). */
    public void onAbort(Runnable listener) {
        if (aborted) listener.run(); else listeners.add(listener);
    }

    void abort() {
        if (aborted) return;
        aborted = true;
        for (Runnable l : listeners) l.run();
        listeners.clear();
    }
}

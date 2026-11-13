package jreact;

/**
 * Where {@link Head} goes in the browser: {@code JReactWeb.createRoot} installs one that edits {@code document.head}. A page rendered on
 * the server collects its head with {@link HeadCollector} instead.
 */
public interface HeadHost {

    /**
     * Puts {@code head} into effect for a component at the given tree {@code depth} (deeper wins) and returns what undoes it
     * (when the component goes away the next one down takes over, or the page's own tags come back).
     */
    Runnable apply(Head head, int depth);

    static void install(HeadHost host) { Holder.host = host; }

    static HeadHost current() { return Holder.host; }

    final class Holder {
        private Holder() {}
        static HeadHost host;
    }
}

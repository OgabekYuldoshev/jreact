package jreact;

/** An effect that returns its cleanup (or null). */
@FunctionalInterface
public interface Effect {
    Runnable run();
}

package jreact;

/** Synthetic event passed to on* handlers. */
public interface Event {
    String type();
    /** Current value of the target input, or null. */
    String value();
    boolean checked();
    void preventDefault();
    void stopPropagation();
}

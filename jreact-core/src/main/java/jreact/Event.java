package jreact;

/** Synthetic event passed to on* handlers. */
public interface Event {
    String type();
    /** Current value of the target input, or null. */
    String value();
    boolean checked();
    void preventDefault();
    void stopPropagation();
    /** True for a click that should not be handled as a plain left click: Ctrl/Cmd/Shift/Alt held or a non-primary button. */
    default boolean modified() { return false; }
    /** The fields of the form this event belongs to (a submit event, or an event of a control inside a form), URL-encoded: {@code name=Ada&tag=a&tag=b}. Empty when there is no form. Files are not included. */
    default String formData() { return ""; }
}

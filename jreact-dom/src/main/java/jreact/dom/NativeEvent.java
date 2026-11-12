package jreact.dom;

/** A platform event as seen by the delegated root listener (browser: a thin wrapper of a JS Event). */
public interface NativeEvent<N> {
    /** Native type: "click", "input", "keydown"... */
    String type();

    /** The deepest node the event happened on. */
    N target();

    /** Lower-case tag name of the target, e.g. "input". */
    String targetTag();

    /** {@code type} attribute of the target input ("checkbox", "text"...), or null. */
    String targetType();

    /** Current value of the target input, or null. */
    String value();

    boolean checked();

    void preventDefault();

    /** Ctrl/Cmd/Shift/Alt held or a non-primary button. */
    default boolean modified() { return false; }

    /** URL-encoded fields of the form around the target (see {@code Event.formData}). */
    default String formData() { return ""; }
}

package jreact.dom;

import java.util.function.Consumer;

/**
 * The few DOM operations {@link DomHost} needs. The browser implementation (TeaVM JSO) is a one-liner per
 * method; tests use an in-memory tree. Keeping this tiny is what makes all DOM rules testable on the JVM.
 *
 * @param <N> node type (a JS {@code Node} in the browser)
 */
public interface DomOps<N> {

    N createElement(String tag);

    N createTextNode(String text);

    void setAttribute(N element, String name, String value);

    void removeAttribute(N element, String name);

    /** Sets a DOM property ({@code value}, {@code checked}...). Value is a String or a Boolean. */
    void setProperty(N element, String name, Object value);

    /** {@code element.style.setProperty(cssName, value)}; a null value removes the property. */
    void setStyleProperty(N element, String cssName, String value);

    void setTextContent(N textNode, String text);

    /** Appends or moves: a child that already has a parent must be moved. */
    void appendChild(N parent, N child);

    /** Inserts or moves, same as {@link #appendChild}. */
    void insertBefore(N parent, N child, N before);

    void removeChild(N parent, N child);

    N parentNode(N node);

    /** One free slot per node for the host's bookkeeping (browser: an expando property). */
    Object getData(N node);

    void setData(N node, Object data);

    /**
     * Listens for {@code type} on {@code container}. Events that do not bubble ({@code scroll}) must be listened
     * to in the capture phase so delegation still sees them.
     */
    void addEventListener(N container, String type, Consumer<NativeEvent<N>> listener);
}

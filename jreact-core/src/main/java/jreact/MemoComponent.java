package jreact;

import java.util.Objects;

/**
 * A component that skips rendering when its props are equal to the previous ones (React's memo).
 * Props are records, so {@code equals} compares them field by field. Fields that are new objects on every
 * render (a fresh lambda, {@code children}) make props differ, exactly like in React, so pass stable values.
 *
 * <p>Override {@link #arePropsEqual} for a custom comparison. A memo component still re-renders on its own
 * state changes and when a context it reads changes.
 */
public abstract class MemoComponent<P> extends Component<P> {

    @Override
    protected boolean arePropsEqual(P prev, P next) { return Objects.equals(prev, next); }
}

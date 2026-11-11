package jreact;

import java.util.List;
import java.util.Objects;

/**
 * Catches errors thrown while rendering (or in useEffect) of its children and shows {@code fallback} instead,
 * so one broken component does not take down the whole app. Like in React it does not catch errors in event
 * handlers, and it cannot catch errors of its own fallback (those go to the next boundary above).
 *
 * <pre>{@code
 * <ErrorBoundary fallback={(err, reset) -> <div>Xato: {err.getMessage()} <button onClick={e -> reset.run()}>Qayta</button></div>}>
 *     <Widget />
 * </ErrorBoundary>
 * }</pre>
 *
 * Props: {@code fallback} (nothing is shown if null), {@code onError} (logging), {@code resetKeys} (the error is
 * cleared when this list changes, e.g. {@code resetKeys={List.of(userId)}}), {@code children}.
 */
public final class ErrorBoundary extends Component<ErrorBoundary.Props> {

    @FunctionalInterface
    public interface Fallback { Element render(Throwable error, Runnable reset); }

    @FunctionalInterface
    public interface Handler { void handle(Throwable error, String componentStack); }

    public record Props(Fallback fallback, Handler onError, List<Object> resetKeys, List<Object> children) {}

    @Override
    public Element render(Props props) {
        return Jsx.fragment(null, props.children() == null ? new Object[0] : props.children().toArray());
    }

    @Override protected boolean isErrorBoundary() { return true; }

    @Override
    protected Element renderError(Props props, Throwable error, Runnable reset) {
        return props.fallback() == null ? null : props.fallback().render(error, reset);
    }

    @Override
    protected void didCatch(Props props, Throwable error, String componentStack) {
        if (props.onError() != null) props.onError().handle(error, componentStack);
    }

    @Override
    protected boolean shouldResetError(Props prev, Props next) {
        return !Objects.equals(prev.resetKeys(), next.resetKeys());
    }
}

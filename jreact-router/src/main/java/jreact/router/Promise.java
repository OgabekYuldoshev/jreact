package jreact.router;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A value that may arrive later (JavaScript's Promise, in Java without threads or an event loop). Loaders and actions
 * return one when they have to wait; whoever completes it calls {@link #resolve} or {@link #reject} (for example from a
 * timer or a fetch callback). Callbacks run immediately when the promise settles, on the thread that settled it.
 */
public final class Promise<T> {

    private boolean done, failed;
    private T value;
    private Throwable error;
    private List<Runnable> waiting;

    private Promise() {}

    /** A promise that is not settled yet. */
    public static <T> Promise<T> pending() { return new Promise<>(); }

    public static <T> Promise<T> resolved(T value) { Promise<T> p = new Promise<>(); p.resolve(value); return p; }

    public static <T> Promise<T> rejected(Throwable error) { Promise<T> p = new Promise<>(); p.reject(error); return p; }

    /** {@code value} itself if it already is a promise, otherwise a resolved one. */
    @SuppressWarnings("unchecked")
    public static <T> Promise<T> from(Object value) { return value instanceof Promise<?> p ? (Promise<T>) p : resolved((T) value); }

    /** Settles with a value. Returns false (and does nothing) when it was settled already. */
    public boolean resolve(T result) {
        if (done) return false;
        done = true; value = result;
        flush();
        return true;
    }

    public boolean reject(Throwable cause) {
        if (done) return false;
        done = true; failed = true; error = cause;
        flush();
        return true;
    }

    public boolean isDone() { return done; }

    public boolean isRejected() { return done && failed; }

    /** The value of a resolved promise, null otherwise. */
    public T value() { return value; }

    /** The reason of a rejected promise, null otherwise. */
    public Throwable error() { return error; }

    /** Runs {@code callback(value, error)} once settled (right away if it already is). Exactly one of the two is non-null... unless the value itself is null. */
    public void whenDone(BiConsumer<T, Throwable> callback) {
        Runnable r = () -> callback.accept(value, error);
        if (done) r.run();
        else (waiting == null ? waiting = new ArrayList<>() : waiting).add(r);
    }

    /** A promise of {@code f(value)}. A rejection passes through, an exception in {@code f} rejects. */
    public <U> Promise<U> then(Function<? super T, ? extends U> f) {
        Promise<U> next = pending();
        whenDone((v, e) -> {
            if (e != null) { next.reject(e); return; }
            try { next.resolve(f.apply(v)); } catch (Throwable t) { next.reject(t); }
        });
        return next;
    }

    /** Like {@link #then}, for an {@code f} that itself returns a promise. */
    public <U> Promise<U> thenCompose(Function<? super T, Promise<U>> f) {
        Promise<U> next = pending();
        whenDone((v, e) -> {
            if (e != null) { next.reject(e); return; }
            try { f.apply(v).whenDone((v2, e2) -> { if (e2 != null) next.reject(e2); else next.resolve(v2); }); }
            catch (Throwable t) { next.reject(t); }
        });
        return next;
    }

    /** Turns a rejection into a value. */
    public Promise<T> recover(Function<Throwable, ? extends T> f) {
        Promise<T> next = pending();
        whenDone((v, e) -> {
            if (e == null) { next.resolve(v); return; }
            try { next.resolve(f.apply(e)); } catch (Throwable t) { next.reject(t); }
        });
        return next;
    }

    /** Resolves with all values (in order) once every promise resolved; rejects with the first failure. */
    public static Promise<List<Object>> all(List<? extends Promise<?>> promises) {
        Promise<List<Object>> result = pending();
        if (promises.isEmpty()) { result.resolve(List.of()); return result; }
        Object[] values = new Object[promises.size()];
        int[] left = {promises.size()};
        for (int i = 0; i < promises.size(); i++) {
            int index = i;
            promises.get(i).whenDone((v, e) -> {
                if (e != null) { result.reject(e); return; }
                values[index] = v;
                if (--left[0] == 0) result.resolve(java.util.Arrays.asList(values));
            });
        }
        return result;
    }

    private void flush() {
        List<Runnable> run = waiting;
        waiting = null;
        if (run != null) for (Runnable r : run) r.run();
    }
}

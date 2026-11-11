package jreact;

import java.util.function.UnaryOperator;

public interface State<T> {
    T get();
    void set(T value);
    void update(UnaryOperator<T> fn);
}

package jreact;

import java.util.AbstractList;
import java.util.RandomAccess;

/**
 * The {@code children} prop of a component: the JSX children written between its tags. Their position is
 * fixed in the source, so unlike a list built from data they do not need keys. The marker lets dev-mode
 * checks tell the two apart (see {@link Jsx#isStaticChildren}).
 */
final class StaticChildren extends AbstractList<Object> implements RandomAccess {
    private final Object[] items;

    StaticChildren(Object[] items) { this.items = items; }

    @Override public Object get(int index) { return items[index]; }

    @Override public int size() { return items.length; }
}

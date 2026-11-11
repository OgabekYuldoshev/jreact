package app;

import java.util.Map;
import java.util.function.Consumer;
import jreact.Context;

/** What the cart provider shares with the tree: the lines plus the actions that change them. */
public record Cart(Map<Integer, Integer> lines, Consumer<Integer> add, Consumer<Integer> dec,
                   Consumer<Integer> remove, Runnable clear) {

    public static final Context<Cart> CONTEXT = Context.create(null, "cart");

    public int count() { return lines.values().stream().mapToInt(Integer::intValue).sum(); }

    public int subtotal() {
        return lines.entrySet().stream().mapToInt(e -> Catalog.BY_ID.get(e.getKey()).price() * e.getValue()).sum();
    }

    public int quantity(int productId) { return lines.getOrDefault(productId, 0); }
}

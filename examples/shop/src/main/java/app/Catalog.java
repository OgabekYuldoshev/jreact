package app;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The product data and the search/filter/sort used by the shop page. */
final class Catalog {
    private Catalog() {}

    static final List<Product> ALL = List.of(
        new Product(1, "Ethiopia Yirgacheffe", "Coffee", 1890, "☕", 48),
        new Product(2, "Colombia Huila", "Coffee", 1650, "☕", 45),
        new Product(3, "Espresso Blend", "Coffee", 1450, "☕", 46),
        new Product(4, "Swiss Water Decaf", "Coffee", 1750, "☕", 41),
        new Product(5, "Sencha Green", "Tea", 1250, "🍵", 44),
        new Product(6, "Jasmine Pearls", "Tea", 1990, "🍵", 47),
        new Product(7, "Earl Grey Reserve", "Tea", 1150, "🫖", 43),
        new Product(8, "Masala Chai", "Tea", 1050, "🫖", 45),
        new Product(9, "Ceramic Dripper", "Gear", 3200, "⚗️", 44),
        new Product(10, "Gooseneck Kettle", "Gear", 5900, "🫖", 48),
        new Product(11, "Burr Grinder", "Gear", 8900, "⚙️", 47),
        new Product(12, "Digital Scale", "Gear", 2900, "⚖️", 42)
    );

    static final Map<Integer, Product> BY_ID = ALL.stream().collect(Collectors.toMap(Product::id, Function.identity()));

    static final List<String> CATEGORIES = List.of("All", "Coffee", "Tea", "Gear");

    static List<Product> filter(String query, String category, String sort) {
        String q = query.trim().toLowerCase();
        Comparator<Product> order = switch (sort) {
            case "price-asc" -> Comparator.comparingInt(Product::price);
            case "price-desc" -> Comparator.comparingInt(Product::price).reversed();
            case "rating" -> Comparator.comparingInt(Product::rating).reversed();
            default -> Comparator.comparingInt(Product::id);
        };
        return ALL.stream()
                .filter(p -> category.equals("All") || p.category().equals(category))
                .filter(p -> q.isEmpty() || p.name().toLowerCase().contains(q))
                .sorted(order)
                .toList();
    }
}

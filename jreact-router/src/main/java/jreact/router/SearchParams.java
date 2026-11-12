package jreact.router;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The query string as name/value pairs (like the browser's URLSearchParams), immutable: {@code with} and {@code without} return new ones. */
public final class SearchParams {

    private final Map<String, List<String>> values;

    private SearchParams(Map<String, List<String>> values) { this.values = values; }

    public static SearchParams empty() { return new SearchParams(new LinkedHashMap<>()); }

    /** From {@code ?a=1&b=2} (the {@code ?} is optional). */
    public static SearchParams parse(String search) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        String s = search == null ? "" : search;
        if (s.startsWith("?")) s = s.substring(1);
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String name = Uri.decode(eq < 0 ? pair : pair.substring(0, eq), true);
            String value = eq < 0 ? "" : Uri.decode(pair.substring(eq + 1), true);
            map.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return new SearchParams(map);
    }

    /** The first value of {@code name}, or null. */
    public String get(String name) {
        List<String> v = values.get(name);
        return v == null ? null : v.get(0);
    }

    public String get(String name, String fallback) {
        String v = get(name);
        return v == null ? fallback : v;
    }

    public List<String> getAll(String name) { return List.copyOf(values.getOrDefault(name, List.of())); }

    public boolean has(String name) { return values.containsKey(name); }

    public boolean isEmpty() { return values.isEmpty(); }

    /** A copy where {@code name} has only {@code value}. A null or empty value removes it. */
    public SearchParams with(String name, String value) {
        Map<String, List<String>> copy = copy();
        if (value == null || value.isEmpty()) copy.remove(name);
        else copy.put(name, new ArrayList<>(List.of(value)));
        return new SearchParams(copy);
    }

    public SearchParams without(String name) { return with(name, null); }

    private Map<String, List<String>> copy() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        values.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    /** {@code a=1&b=2} without a leading {@code ?}. Add it yourself: {@code "?" + params}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        values.forEach((name, list) -> {
            for (String v : list) {
                if (sb.length() > 0) sb.append('&');
                sb.append(Uri.encodeQuery(name)).append('=').append(Uri.encodeQuery(v));
            }
        });
        return sb.toString();
    }

    @Override public boolean equals(Object o) { return o instanceof SearchParams p && values.equals(p.values); }

    @Override public int hashCode() { return values.hashCode(); }
}

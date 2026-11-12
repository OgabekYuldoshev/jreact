package jreact.router;

import jreact.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests of jreact.Json (lives here because jreact-core has no test folder; runs with the router tests). */
public final class JsonTest {

    static int passed;
    static final java.util.List<String> failures = new java.util.ArrayList<>();

    static void test(String name, Runnable body) {
        try { body.run(); passed++; } catch (Throwable t) { failures.add(name + ": " + t); }
    }

    static void eq(Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) throw new AssertionError("expected: " + expected + "\n  actual: " + actual);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        test("objects keep key order, numbers are Long or Double, nesting works", () -> {
            Map<String, Object> m = (Map<String, Object>) Json.parse(" {\"b\": 1, \"a\": [true, false, null, 2.5, -3, 1e2], \"c\": {\"d\": \"x\"}} ");
            eq("[b, a, c]", m.keySet());
            eq(1L, m.get("b"));
            eq("[true, false, null, 2.5, -3, 100.0]", m.get("a"));
            eq(Long.class, ((List<Object>) m.get("a")).get(4).getClass());
            eq("x", ((Map<String, Object>) m.get("c")).get("d"));
        });

        test("string escapes, unicode and surrogate pairs", () -> {
            eq("a\"b\\c/d\n\t\u00e9\ud83d\ude00", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\\ud83d\\ude00\""));
        });

        test("stringify escapes quotes, control characters and < > & so the output is safe in <script>", () -> {
            eq("\"a\\\"b\\n\\u003c\\/\"".replace("\\/", "/"), Json.stringify("a\"b\n</"));
            eq("\"\\u003cscript\\u003e\\u0026\"", Json.stringify("<script>&"));
            eq("\"\\u0001\"", Json.stringify("\u0001"));
        });

        test("stringify then parse gives the same value (maps, lists, numbers, booleans, null)", () -> {
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("id", 7L);
            in.put("price", 9.5);
            in.put("tags", List.of("a", "b"));
            in.put("done", true);
            in.put("none", null);
            String text = Json.stringify(in);
            eq("{\"id\":7,\"price\":9.5,\"tags\":[\"a\",\"b\"],\"done\":true,\"none\":null}", text);
            eq(text, Json.stringify(Json.parse(text)));
        });

        test("a whole double is written without .0 and NaN becomes null", () -> {
            eq("3", Json.stringify(3.0));
            eq("null", Json.stringify(Double.NaN));
        });

        test("Writable decides its own shape", () -> {
            Json.Writable w = () -> Map.of("k", "v");
            eq("[{\"k\":\"v\"}]", Json.stringify(List.of(w)));
        });

        test("bad input is a ParseException with the position, not a random exception", () -> {
            for (String bad : new String[]{"", "{", "[1,]", "{\"a\" 1}", "\"abc", "tru", "1 2", "{\"a\":}", "\"\\x\"", "-"}) {
                try { Json.parse(bad); throw new AssertionError("accepted: " + bad); }
                catch (Json.ParseException expected) { /* ok */ }
            }
        });

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        if (!failures.isEmpty()) System.exit(1);
    }
}

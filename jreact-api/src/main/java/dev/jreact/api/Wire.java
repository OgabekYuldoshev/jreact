package dev.jreact.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checked reads of JSON values, used by generated code. A value of the wrong shape is a 400 {@link RpcException} that names the
 * place ({@code args[1].title: expected a string}), never a ClassCastException.
 */
public final class Wire {
    private Wire() {}

    private static RpcException bad(String path, String expected, Object got) {
        return new RpcException(400, path + ": expected " + expected + (got == null ? "" : ", got " + (got instanceof String ? "a string" : got instanceof Boolean ? "a boolean" : got instanceof Number ? "a number" : got instanceof List ? "an array" : "an object")));
    }

    public static String str(Object j, String path) {
        if (j == null) return null;
        if (j instanceof String s) return s;
        throw bad(path, "a string", j);
    }

    public static boolean bool(Object j, String path) {
        if (j instanceof Boolean b) return b;
        throw bad(path, "true or false", j);
    }

    public static Boolean boolOrNull(Object j, String path) { return j == null ? null : bool(j, path); }

    public static long longv(Object j, String path) {
        if (j instanceof Long l) return l;
        if (j instanceof Double d && d == Math.rint(d) && Math.abs(d) < 9e15) return (long) (double) d;
        throw bad(path, "an integer", j);
    }

    public static Long longOrNull(Object j, String path) { return j == null ? null : longv(j, path); }

    public static int intv(Object j, String path) {
        long l = longv(j, path);
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) throw new RpcException(400, path + ": " + l + " does not fit in an int");
        return (int) l;
    }

    public static Integer intOrNull(Object j, String path) { return j == null ? null : intv(j, path); }

    public static double doublev(Object j, String path) {
        if (j instanceof Number n) return n.doubleValue();
        throw bad(path, "a number", j);
    }

    public static Double doubleOrNull(Object j, String path) { return j == null ? null : doublev(j, path); }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object j, String path) {
        if (j == null) return null;
        if (j instanceof List<?> l) return (List<Object>) l;
        throw bad(path, "an array", j);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object j, String path) {
        if (j == null) return null;
        if (j instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw bad(path, "an object", j);
    }

    // ---- java.time as ISO-8601 strings ("2026-11-13", "2026-11-13T09:30:00", "09:30", "2026-11-13T04:30:00Z")

    public static java.time.LocalDate localDate(Object j, String path) {
        String s = str(j, path);
        if (s == null) return null;
        try { return java.time.LocalDate.parse(s); } catch (RuntimeException e) { throw bad(path, "a date like 2026-11-13", s); }
    }

    public static java.time.LocalDateTime localDateTime(Object j, String path) {
        String s = str(j, path);
        if (s == null) return null;
        try { return java.time.LocalDateTime.parse(s); } catch (RuntimeException e) { throw bad(path, "a date and time like 2026-11-13T09:30:00", s); }
    }

    public static java.time.LocalTime localTime(Object j, String path) {
        String s = str(j, path);
        if (s == null) return null;
        try { return java.time.LocalTime.parse(s); } catch (RuntimeException e) { throw bad(path, "a time like 09:30", s); }
    }

    public static java.time.Instant instant(Object j, String path) {
        String s = str(j, path);
        if (s == null) return null;
        try { return java.time.Instant.parse(s); } catch (RuntimeException e) { throw bad(path, "an instant like 2026-11-13T04:30:00Z", s); }
    }

    /** The arguments of a call: exactly {@code count} of them. */
    public static void args(List<Object> args, int count) {
        if (args == null || args.size() != count) throw new RpcException(400, "expected " + count + " argument(s), got " + (args == null ? 0 : args.size()));
    }

    /** {@code values} is {@code MyEnum.values()} (no reflection, so it also works in the browser). */
    public static <E extends Enum<E>> E enumv(E[] values, Object j, String path) {
        if (j == null) return null;
        String name = str(j, path);
        for (E e : values) if (e.name().equals(name)) return e;
        throw new RpcException(400, path + ": \"" + name + "\" is not one of the allowed values");
    }

    public static Map<String, Object> newObject() { return new LinkedHashMap<>(); }

    public static List<Object> newArray() { return new ArrayList<>(); }
}

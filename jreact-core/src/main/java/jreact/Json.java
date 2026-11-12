package jreact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer with no reflection, so it runs the same on the JVM and in the browser (TeaVM).
 * <p>
 * {@link #parse} gives {@code Map<String,Object>} (keeps key order), {@code List<Object>}, {@code String}, {@code Long} (integers that fit),
 * {@code Double}, {@code Boolean} or null. {@link #stringify} writes those same types, plus any {@code Iterable} and {@code Record}-like
 * values that implement {@link Json.Writable}. Strings are escaped; the output never contains a raw {@code <} so it is safe inside a {@code <script>}.
 */
public final class Json {
    private Json() {}

    /** Implement to control how an object is written (usually: return a Map). */
    public interface Writable {
        Object toJson();
    }

    public static final class ParseException extends RuntimeException {
        public ParseException(String message, int at) { super(message + " at " + at); }
    }

    // ---------------------------------------------------------------- parse

    public static Object parse(String text) {
        if (text == null) throw new ParseException("no input", 0);
        Parser p = new Parser(text);
        p.skip();
        Object v = p.value();
        p.skip();
        if (p.i != text.length()) throw new ParseException("unexpected trailing characters", p.i);
        return v;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        void skip() { while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\n' || s.charAt(i) == '\r' || s.charAt(i) == '\t')) i++; }

        char peek() {
            if (i >= s.length()) throw new ParseException("unexpected end", i);
            return s.charAt(i);
        }

        void expect(char c) {
            if (peek() != c) throw new ParseException("expected '" + c + "'", i);
            i++;
        }

        Object value() {
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { word("true"); return Boolean.TRUE; }
            if (c == 'f') { word("false"); return Boolean.FALSE; }
            if (c == 'n') { word("null"); return null; }
            return number();
        }

        void word(String w) {
            if (!s.startsWith(w, i)) throw new ParseException("unexpected token", i);
            i += w.length();
        }

        Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            expect('{');
            skip();
            if (peek() == '}') { i++; return out; }
            while (true) {
                skip();
                String key = string();
                skip();
                expect(':');
                skip();
                out.put(key, value());
                skip();
                char c = peek();
                i++;
                if (c == '}') return out;
                if (c != ',') throw new ParseException("expected ',' or '}'", i - 1);
            }
        }

        List<Object> array() {
            List<Object> out = new ArrayList<>();
            expect('[');
            skip();
            if (peek() == ']') { i++; return out; }
            while (true) {
                skip();
                out.add(value());
                skip();
                char c = peek();
                i++;
                if (c == ']') return out;
                if (c != ',') throw new ParseException("expected ',' or ']'", i - 1);
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw new ParseException("unterminated string", i);
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c < 0x20) throw new ParseException("control character in string", i - 1);
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw new ParseException("unterminated escape", i);
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u': {
                        if (i + 4 > s.length()) throw new ParseException("bad \\u escape", i);
                        int code = 0;
                        for (int k = 0; k < 4; k++) {
                            int d = Character.digit(s.charAt(i + k), 16);
                            if (d < 0) throw new ParseException("bad \\u escape", i + k);
                            code = code * 16 + d;
                        }
                        i += 4;
                        b.append((char) code);
                        break;
                    }
                    default: throw new ParseException("bad escape \\" + e, i - 1);
                }
            }
        }

        Object number() {
            int start = i;
            if (peek() == '-') i++;
            boolean real = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') { i++; continue; }
                if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { real = true; i++; continue; }
                break;
            }
            String t = s.substring(start, i);
            if (t.isEmpty() || t.equals("-")) throw new ParseException("unexpected character", start);
            try {
                if (!real && t.length() <= 18) return Long.valueOf(Long.parseLong(t));
                return Double.valueOf(Double.parseDouble(t));
            } catch (NumberFormatException ex) {
                throw new ParseException("bad number " + t, start);
            }
        }
    }

    // ---------------------------------------------------------------- stringify

    public static String stringify(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof Writable w) { write(b, w.toJson()); return; }
        if (v instanceof String s) { quote(b, s); return; }
        if (v instanceof Boolean) { b.append(v); return; }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
            return;
        }
        if (v instanceof Number) { b.append(((Number) v).longValue()); return; }
        if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) m).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            b.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) b.append(',');
                first = false;
                write(b, o);
            }
            b.append(']');
            return;
        }
        if (v instanceof Object[] arr) {
            b.append('[');
            for (int k = 0; k < arr.length; k++) {
                if (k > 0) b.append(',');
                write(b, arr[k]);
            }
            b.append(']');
            return;
        }
        throw new IllegalArgumentException("cannot write " + v.getClass().getName() + " as JSON (implement Json.Writable)");
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    // < > & and the two line separators as \\u escapes: safe inside <script> and HTML
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == ' ' || c == ' ') {
                        String h = Integer.toHexString(c);
                        b.append("\\u");
                        for (int pad = h.length(); pad < 4; pad++) b.append('0');
                        b.append(h);
                    } else b.append(c);
            }
        }
        b.append('"');
    }
}

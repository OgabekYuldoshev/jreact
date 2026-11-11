package jreact.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pass 1 of the compiler: finds every component class and the parameters of its
 * {@code record Props(...)}, so that {@code <TodoItem todo={t} />} can be compiled
 * into a typed {@code new TodoItem.Props(t, ...)} call (javac then checks the types).
 */
public final class PropsIndex {

    public record Param(String type, String name) {}

    public record Info(String component, List<Param> params) {
        public boolean has(String name) {
            return params.stream().anyMatch(p -> p.name().equals(name));
        }
        public String names() {
            return params.stream().map(Param::name).collect(Collectors.joining(", "));
        }
    }

    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PROPS = Pattern.compile("\\brecord\\s+Props\\s*\\(");
    private static final Pattern ANNOTATION = Pattern.compile("@[\\w.]+(\\s*\\([^)]*\\))?");

    private final Map<String, Info> byName = new HashMap<>();

    public Info find(String tagName) {
        int dot = tagName.lastIndexOf('.');
        return byName.get(dot < 0 ? tagName : tagName.substring(dot + 1));
    }

    public Collection<Info> all() { return byName.values(); }

    public void scan(String source) {
        String s = blankCommentsAndStrings(source);
        List<int[]> classes = new ArrayList<>();      // {nameStart, nameEnd, bodySearchStart}
        Matcher cm = CLASS.matcher(s);
        while (cm.find()) classes.add(new int[]{cm.start(1), cm.end(1), cm.end()});

        for (int i = 0; i < classes.size(); i++) {
            int[] c = classes.get(i);
            int limit = i + 1 < classes.size() ? classes.get(i + 1)[0] : s.length();
            Matcher rm = PROPS.matcher(s).region(c[2], limit);
            if (!rm.find()) continue;
            int open = rm.end() - 1;
            int close = matchParen(s, open);
            if (close < 0) continue;
            String name = s.substring(c[0], c[1]);
            byName.put(name, new Info(name, parseParams(source.substring(open + 1, close))));
        }
    }

    private static List<Param> parseParams(String text) {
        List<Param> params = new ArrayList<>();
        for (String raw : splitTopLevel(text)) {
            String p = ANNOTATION.matcher(raw).replaceAll(" ").trim();
            if (p.isEmpty()) continue;
            int sp = lastSpaceOutsideGenerics(p);
            if (sp < 0) continue;
            params.add(new Param(p.substring(0, sp).trim(), p.substring(sp + 1).trim()));
        }
        return params;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<' || ch == '(' || ch == '[') depth++;
            else if (ch == '>' || ch == ')' || ch == ']') depth--;
            else if (ch == ',' && depth == 0) { parts.add(text.substring(start, i)); start = i + 1; }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static int lastSpaceOutsideGenerics(String p) {
        int depth = 0, last = -1;
        for (int i = 0; i < p.length(); i++) {
            char ch = p.charAt(i);
            if (ch == '<') depth++;
            else if (ch == '>') depth--;
            else if (Character.isWhitespace(ch) && depth == 0) last = i;
        }
        return last;
    }

    private static int matchParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')' && --depth == 0) return i;
        }
        return -1;
    }

    /** Replaces comments and string contents with spaces (keeps indices), tolerant of JSX text. */
    static String blankCommentsAndStrings(String src) {
        char[] a = src.toCharArray();
        int i = 0;
        while (i < a.length) {
            char c = a[i];
            if (c == '/' && i + 1 < a.length && a[i + 1] == '/') {
                while (i < a.length && a[i] != '\n') a[i++] = ' ';
            } else if (c == '/' && i + 1 < a.length && a[i + 1] == '*') {
                while (i < a.length && !(a[i] == '*' && i + 1 < a.length && a[i + 1] == '/')) {
                    if (a[i] != '\n') a[i] = ' ';
                    i++;
                }
                if (i < a.length) { a[i] = ' '; a[i + 1] = ' '; i += 2; }
            } else if (c == '"') {
                int j = i + 1;
                while (j < a.length && a[j] != '"' && a[j] != '\n') j += a[j] == '\\' ? 2 : 1;
                if (j < a.length && a[j] == '"') {
                    for (int k = i + 1; k < j; k++) a[k] = ' ';
                    i = j + 1;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
        return new String(a);
    }
}

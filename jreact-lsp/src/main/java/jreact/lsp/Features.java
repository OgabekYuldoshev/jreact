package jreact.lsp;

import jreact.compiler.PropsIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Completion, hover and go-to-definition for JSX tags. Java expressions are left to the Java tooling. */
final class Features {

    private Features() {}

    /** Where the cursor is inside an open tag: {@code <Name attr=... |}. */
    record TagContext(int lt, String name, boolean inName, String prefix, Set<String> given) {}

    private static final Pattern ATTR = Pattern.compile("(?<![\\w$-])([A-Za-z_$][\\w$:-]*)\\s*=");

    static final List<String> HTML_TAGS = List.of("a", "article", "aside", "b", "br", "button", "code", "div", "em", "footer",
            "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "i", "img", "input", "label", "li", "main", "nav",
            "ol", "option", "p", "pre", "section", "select", "small", "span", "strong", "table", "tbody", "td", "textarea",
            "th", "thead", "tr", "ul");

    static final List<String> HTML_ATTRS = List.of("className", "id", "style", "title", "href", "src", "alt", "type", "value",
            "placeholder", "disabled", "checked", "name", "htmlFor", "tabIndex", "role", "target", "rel", "min", "max", "step",
            "rows", "cols", "selected", "readOnly", "required", "autoFocus", "maxLength", "onClick", "onDoubleClick",
            "onInput", "onChange", "onSubmit", "onKeyDown", "onKeyUp", "onFocus", "onBlur", "onMouseDown", "onMouseUp",
            "onScroll");

    // ------------------------------------------------------------ cursor context

    static TagContext tagAt(String t, int off) {
        int lineStart = t.lastIndexOf('\n', off - 1) + 1;
        int quotes = 0;
        for (int k = lineStart; k < off; k++) {
            char c = t.charAt(k);
            if (c == '\\') k++; else if (c == '"') quotes++;
        }
        if (quotes % 2 == 1) return null;                         // inside a string literal

        int depth = 0;
        for (int i = off - 1, limit = Math.max(0, off - 4000); i >= limit; i--) {
            char c = t.charAt(i);
            if (c == '"') {
                int j = t.lastIndexOf('"', i - 1);
                if (j < 0 || j < t.lastIndexOf('\n', i - 1)) return null;
                i = j;
            } else if (c == '}') {
                depth++;
            } else if (c == '{') {
                if (depth == 0) return null;                      // inside a {expression}
                depth--;
            } else if (depth == 0) {
                if (c == '>' || c == ';') return null;            // the tag already ended, or plain Java
                if (c == '<') {
                    if (i + 1 < off && t.charAt(i + 1) == '/') return null;
                    char prev = i > 0 ? t.charAt(i - 1) : ' ';
                    if (Character.isJavaIdentifierPart(prev) || prev == ')' || prev == ']' || prev == '.') return null;   // generics
                    int s = i + 1, e = s;
                    while (e < off && nameChar(t.charAt(e))) e++;
                    boolean inName = e == off;
                    if (!inName && (e == s || !(Character.isWhitespace(t.charAt(e)) || t.charAt(e) == '/'))) return null;
                    Set<String> given = new LinkedHashSet<>();
                    if (!inName) {
                        Matcher m = ATTR.matcher(t.substring(e, off));
                        while (m.find()) given.add(m.group(1));
                    }
                    return new TagContext(i, t.substring(s, e), inName, t.substring(s, e), given);
                }
            }
        }
        return null;
    }

    private static boolean nameChar(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.' || c == '-'; }

    // ------------------------------------------------------------ completion

    static List<Object> completion(Workspace ws, String text, int off) {
        TagContext ctx = tagAt(text, off);
        List<Object> items = new ArrayList<>();
        if (ctx == null) return items;
        PropsIndex index = ws.index();

        if (ctx.inName()) {
            index.all().stream().sorted(Comparator.comparing(PropsIndex.Info::component)).forEach(info -> {
                Map<String, Object> item = item(info.component(), 7, "component", "0" + info.component());
                item.put("documentation", markdown(propsSignature(info)));
                items.add(item);
            });
            for (String tag : HTML_TAGS) items.add(item(tag, 14, "html element", "1" + tag));
            return items;
        }

        PropsIndex.Info info = Character.isUpperCase(ctx.name().isEmpty() ? 'x' : ctx.name().charAt(0)) ? index.find(ctx.name()) : null;
        if (info != null) {
            for (PropsIndex.Param p : info.params()) {
                if (p.name().equals("children") || ctx.given().contains(p.name())) continue;
                items.add(attr(p.name(), p.type(), "0" + p.name()));
            }
        } else if (!Character.isUpperCase(ctx.name().isEmpty() ? 'x' : ctx.name().charAt(0))) {
            for (String a : HTML_ATTRS) if (!ctx.given().contains(a)) items.add(attr(a, a.startsWith("on") ? "EventHandler" : "value", "1" + a));
        }
        if (!ctx.given().contains("key")) items.add(attr("key", "Object", "2key"));
        if (!ctx.given().contains("ref") && !Character.isUpperCase(ctx.name().isEmpty() ? 'x' : ctx.name().charAt(0))) {
            items.add(attr("ref", "Ref<?>", "2ref"));
        }
        return items;
    }

    private static Map<String, Object> attr(String name, String type, String sort) {
        Map<String, Object> item = item(name, 10, type, sort);
        item.put("insertText", name + "={$1}");
        item.put("insertTextFormat", 2);
        return item;
    }

    private static Map<String, Object> item(String label, int kind, String detail, String sort) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("kind", kind);
        m.put("detail", detail);
        m.put("sortText", sort);
        return m;
    }

    // ------------------------------------------------------------ hover / definition

    /** What a tag name or attribute under the cursor refers to. */
    private record Target(String word, int start, int end, TagContext tag, boolean isTagName) {}

    private static Target targetAt(String t, int off) {
        int s = off, e = off;
        while (s > 0 && nameChar(t.charAt(s - 1))) s--;
        while (e < t.length() && nameChar(t.charAt(e))) e++;
        if (s == e) return null;
        String word = t.substring(s, e);
        boolean afterLt = s > 0 && t.charAt(s - 1) == '<' || s > 1 && t.charAt(s - 1) == '/' && t.charAt(s - 2) == '<';
        if (afterLt) return new Target(word, s, e, null, true);
        TagContext ctx = tagAt(t, s);
        int k = e;
        while (k < t.length() && Character.isWhitespace(t.charAt(k))) k++;
        if (ctx != null && !ctx.inName() && k < t.length() && t.charAt(k) == '=') return new Target(word, s, e, ctx, false);
        return null;
    }

    static Object hover(Workspace ws, String text, int off) {
        Target tg = targetAt(text, off);
        if (tg == null) return null;
        PropsIndex index = ws.index();
        String md;
        if (tg.isTagName()) {
            PropsIndex.Info info = index.find(tg.word());
            if (info == null) return null;
            md = "**<" + info.component() + ">** component\n\n" + propsSignature(info);
        } else {
            PropsIndex.Info info = index.find(tg.tag().name());
            if (info == null) return null;
            PropsIndex.Param param = info.params().stream().filter(p -> p.name().equals(tg.word())).findFirst().orElse(null);
            if (param == null) return null;
            md = "**" + param.name() + "** prop of <" + info.component() + ">\n\n```java\n" + param.type() + " " + param.name() + "\n```";
        }
        int[] a = Workspace.lineCol(text, tg.start()), b = Workspace.lineCol(text, tg.end());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("contents", markdown(md));
        r.put("range", range(a[0], a[1], b[0], b[1]));
        return r;
    }

    static Object definition(Workspace ws, String text, int off) {
        Target tg = targetAt(text, off);
        if (tg == null || !tg.isTagName()) return null;
        String name = tg.word().substring(tg.word().lastIndexOf('.') + 1);
        Workspace.Location loc = ws.classLocation(name);
        if (loc == null) return null;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uri", loc.uri());
        r.put("range", range(loc.line(), loc.col(), loc.line(), loc.col() + name.length()));
        return r;
    }

    // ------------------------------------------------------------ helpers

    static String propsSignature(PropsIndex.Info info) {
        StringBuilder sb = new StringBuilder("```java\nrecord Props(");
        for (int i = 0; i < info.params().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(info.params().get(i).type()).append(' ').append(info.params().get(i).name());
        }
        return sb.append(")\n```").toString();
    }

    private static Map<String, Object> markdown(String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "markdown");
        m.put("value", value);
        return m;
    }

    static Map<String, Object> range(int l1, int c1, int l2, int c2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", pos(l1, c1));
        m.put("end", pos(l2, c2));
        return m;
    }

    private static Map<String, Object> pos(int line, int ch) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("line", line);
        m.put("character", ch);
        return m;
    }
}

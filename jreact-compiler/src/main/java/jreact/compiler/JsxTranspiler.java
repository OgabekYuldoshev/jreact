package jreact.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles one .jrx file into plain Java.
 *
 * <p>Java code is copied through untouched (javac does the real Java work); only JSX
 * expressions are parsed and replaced with runtime calls, exactly like Babel does for JS:
 *
 * <pre>
 *   &lt;div className="a"&gt;Hi {name}&lt;/div&gt;
 *     → jreact.Jsx.el("div", null, new jreact.Attrs().attr("className", "a"), "Hi ", name)
 *   &lt;TodoItem key={t.id()} todo={t} /&gt;
 *     → jreact.Jsx.c(TodoItem.class, TodoItem::new, t.id(), new TodoItem.Props(t, null, null))
 * </pre>
 *
 * <p>Every newline inside JSX is re-emitted, so line N of the .jrx file is line N of the
 * generated .java file: javac errors and stack traces point at the original source line.
 */
public final class JsxTranspiler {

    private static final String RT = "jreact.";

    private final String file;
    private final String src;
    private final PropsIndex index;

    private int pos = 0, line = 1, col = 1;
    private StringBuilder out = new StringBuilder();

    // Last significant Java token: decides whether '<' starts JSX or is less-than / generics.
    private boolean lastWasValue = false;
    private String lastWord = "";
    private char lastPunct = 0, prevPunct = 0;

    public JsxTranspiler(String file, String src, PropsIndex index) {
        this.file = file;
        this.src = src;
        this.index = index;
    }

    public String transpile() {
        javaCode(false, line, col);
        return out.toString();
    }

    // ------------------------------------------------------------------ Java pass-through

    /**
     * Copies Java source to {@link #out}. When {@code inContainer} it stops (without consuming)
     * at the '}' that closes a JSX expression container {...}.
     */
    private void javaCode(boolean inContainer, int openLine, int openCol) {
        int depth = 0;
        if (inContainer) { lastWasValue = false; lastPunct = '{'; prevPunct = 0; }
        while (pos < src.length()) {
            char c = peek(0);
            if (c == '/' && peek(1) == '/') {
                while (pos < src.length() && peek(0) != '\n') copy();
            } else if (c == '/' && peek(1) == '*') {
                int l = line, cc = col;
                copy(); copy();
                while (!(peek(0) == '*' && peek(1) == '/')) {
                    if (pos >= src.length()) throw error(l, cc, "unterminated comment");
                    copy();
                }
                copy(); copy();
            } else if (c == '"' && peek(1) == '"' && peek(2) == '"') {
                copyTextBlock();
                markValue("");
            } else if (c == '"' || c == '\'') {
                copyQuoted(c);
                markValue("");
            } else if (c == '{') {
                depth++; copy(); markPunct('{');
            } else if (c == '}') {
                if (inContainer && depth == 0) return;
                depth--; copy(); markPunct('}');
            } else if (c == '<' && jsxAllowed() && (Character.isLetter(peek(1)) || peek(1) == '>')) {
                out.append(parseElement());
                markValue("");
            } else if (Character.isJavaIdentifierStart(c)) {
                int s = pos;
                while (pos < src.length() && Character.isJavaIdentifierPart(peek(0))) copy();
                markValue(src.substring(s, pos));
            } else if (Character.isDigit(c)) {
                while (pos < src.length() && (Character.isLetterOrDigit(peek(0)) || peek(0) == '_' || peek(0) == '.')) copy();
                markValue("");
            } else if (Character.isWhitespace(c)) {
                copy();
            } else {
                copy(); markPunct(c);
            }
        }
        if (inContainer) throw error(openLine, openCol, "unclosed '{' in JSX expression");
    }

    /** '<' starts JSX only where an expression may begin: after = ( , ? : { [ && || -> return yield. */
    private boolean jsxAllowed() {
        if (lastWasValue) return lastWord.equals("return") || lastWord.equals("yield");
        if (lastPunct == '>') return prevPunct == '-';
        return lastPunct != 0 && "(,=?:{[&|".indexOf(lastPunct) >= 0;
    }

    private void markValue(String word) { lastWasValue = true; lastWord = word; }

    private void markPunct(char c) {
        prevPunct = lastWasValue ? 0 : lastPunct;
        lastPunct = c;
        lastWasValue = false;
    }

    private void copyQuoted(char q) {
        int l = line, cc = col;
        copy();
        while (true) {
            if (pos >= src.length() || peek(0) == '\n') throw error(l, cc, "unterminated " + (q == '"' ? "string" : "char") + " literal");
            char ch = peek(0);
            if (ch == '\\') { copy(); copy(); }
            else if (ch == q) { copy(); return; }
            else copy();
        }
    }

    private void copyTextBlock() {
        int l = line, cc = col;
        copy(); copy(); copy();
        while (true) {
            if (pos >= src.length()) throw error(l, cc, "unterminated text block");
            if (peek(0) == '\\') { copy(); copy(); }
            else if (peek(0) == '"' && peek(1) == '"' && peek(2) == '"') { copy(); copy(); copy(); return; }
            else copy();
        }
    }

    /** Transpiles the Java expression inside {...} into a separate buffer. pos ends on the '}'. */
    private String captureJava(int openLine, int openCol) {
        StringBuilder saved = out;
        boolean sv = lastWasValue; String sw = lastWord; char sp = lastPunct, spp = prevPunct;
        out = new StringBuilder();
        javaCode(true, openLine, openCol);
        String code = out.toString();
        out = saved;
        lastWasValue = sv; lastWord = sw; lastPunct = sp; prevPunct = spp;
        return code;
    }

    // ------------------------------------------------------------------ JSX

    private static final class Attr {
        String name;          // null for spread
        String code;
        boolean spread, isString;
        int nl, line, col;
    }

    private record Children(List<String> items, int trailingNl) {}

    private String parseElement() {
        int sLine = line, sCol = col;
        expect('<');
        if (peek(0) == '>') {
            advance();
            Children ch = parseChildren("", sLine, sCol);
            return RT + "Jsx.fragment(null" + joinChildren(ch) + ")";
        }

        String name = readName(true);
        List<Attr> attrs = new ArrayList<>();
        int trailingNl = 0;
        boolean selfClosing = false;

        while (true) {
            int nl = skipWs();
            if (pos >= src.length()) throw error(sLine, sCol, "unclosed tag <" + name + ">");
            char c = peek(0);
            if (c == '/') {
                advance(); expect('>');
                selfClosing = true; trailingNl += nl;
                break;
            }
            if (c == '>') { advance(); trailingNl += nl; break; }

            Attr a = new Attr();
            a.nl = nl; a.line = line; a.col = col;
            if (c == '{') {
                advance();
                a.nl += skipWs();
                if (!(peek(0) == '.' && peek(1) == '.' && peek(2) == '.'))
                    throw error(a.line, a.col, "expected an attribute or {...spread} in <" + name + ">");
                advance(); advance(); advance();
                a.spread = true;
                a.code = captureJava(a.line, a.col);
                expect('}');
            } else if (Character.isLetter(c) || c == '_') {
                a.name = readName(false);
                int nl2 = skipWs();
                if (peek(0) == '=') {
                    advance();
                    nl2 += skipWs();
                    a.code = attrValue(a);
                } else {
                    a.code = "true";
                }
                a.nl += nl2;
            } else {
                throw error(line, col, "unexpected '" + c + "' in tag <" + name + ">");
            }
            attrs.add(a);
        }

        Children children = selfClosing ? new Children(List.of(), 0) : parseChildren(name, sLine, sCol);
        boolean isComponent = Character.isUpperCase(name.charAt(0)) || name.contains(".");
        return isComponent
                ? componentCall(name, attrs, children, trailingNl, sLine, sCol)
                : hostCall(name, attrs, children, trailingNl);
    }

    private String attrValue(Attr a) {
        char c = peek(0);
        if (c == '"' || c == '\'') {
            int l = line, cc = col;
            advance();
            int s = pos;
            while (pos < src.length() && peek(0) != c) advance();
            if (pos >= src.length()) throw error(l, cc, "unterminated attribute value");
            String raw = src.substring(s, pos);
            advance();
            a.isString = true;
            return javaString(decodeEntities(raw)) + "\n".repeat(countNl(raw));
        }
        if (c == '{') {
            int l = line, cc = col;
            advance();
            String code = captureJava(l, cc);
            expect('}');
            if (isBlankJava(code)) throw error(l, cc, "JSX attribute '" + a.name + "' has an empty expression");
            return code;
        }
        if (c == '<') return parseElement();
        throw error(line, col, "expected \"value\", {expression} or <element> after " + a.name + "=");
    }

    private Children parseChildren(String name, int sLine, int sCol) {
        List<String> items = new ArrayList<>();
        int pendingNl = 0;
        String shown = name.isEmpty() ? "<>" : "<" + name + ">";
        while (true) {
            if (pos >= src.length()) throw error(sLine, sCol, "unclosed " + shown + ": missing closing tag");
            char c = peek(0);
            if (c == '<' && peek(1) == '/') {
                int cl = line, cc = col;
                advance(); advance();
                pendingNl += skipWs();
                String close = peek(0) == '>' ? "" : readName(true);
                pendingNl += skipWs();
                expect('>');
                if (!close.equals(name)) {
                    String expected = name.isEmpty() ? "</>" : "</" + name + ">";
                    String found = close.isEmpty() ? "</>" : "</" + close + ">";
                    throw error(cl, cc, "expected " + expected + " to close " + shown
                            + " opened at " + sLine + ":" + sCol + ", but found " + found);
                }
                return new Children(items, pendingNl);
            }
            if (c == '<') {
                items.add(nl(pendingNl) + parseElement());
                pendingNl = 0;
            } else if (c == '{') {
                int l = line, cc = col;
                advance();
                String code = captureJava(l, cc);
                expect('}');
                if (isBlankJava(code)) {           // {/* JSX comment */}
                    pendingNl += countNl(code);
                } else {
                    items.add(nl(pendingNl) + code);
                    pendingNl = 0;
                }
            } else if (c == '}') {
                throw error(line, col, "unexpected '}' in JSX text (write {\"}\"} instead)");
            } else {
                int s = pos;
                while (pos < src.length() && peek(0) != '<' && peek(0) != '{' && peek(0) != '}') advance();
                String raw = src.substring(s, pos);
                String text = decodeEntities(cleanJsxText(raw));
                if (!text.isEmpty()) {
                    items.add(nl(pendingNl) + javaString(text));
                    pendingNl = 0;
                }
                pendingNl += countNl(raw);
            }
        }
    }

    // ------------------------------------------------------------------ code generation

    private String hostCall(String tag, List<Attr> attrs, Children children, int trailingNl) {
        String key = "null";
        StringBuilder props = new StringBuilder();
        for (Attr a : attrs) {
            String code = a.code;
            if (a.spread) {
                props.append(nl(a.nl)).append(".spread(").append(code).append(')');
            } else if (a.name.equals("key")) {
                key = nl(a.nl) + code;
            } else if (a.name.equals("ref")) {
                props.append(nl(a.nl)).append(".ref(").append(code).append(')');
            } else if (isEvent(a.name)) {
                if (a.isString) throw error(a.line, a.col, "event handler '" + a.name + "' must be an {expression}, not a string");
                props.append(nl(a.nl)).append(".on(").append(javaString(a.name)).append(", ").append(code).append(')');
            } else {
                props.append(nl(a.nl)).append(".attr(").append(javaString(a.name)).append(", ").append(code).append(')');
            }
        }
        String attrsCode = props.isEmpty() ? "null" : "new " + RT + "Attrs()" + props;
        return RT + "Jsx.el(" + javaString(tag) + ", " + key + ", " + attrsCode
                + joinChildren(children) + nl(trailingNl) + ")";
    }

    private String componentCall(String name, List<Attr> attrs, Children children, int trailingNl, int sLine, int sCol) {
        String key = "null";
        int extraNl = trailingNl;
        Map<String, String> given = new LinkedHashMap<>();
        PropsIndex.Info info = index.find(name);

        for (Attr a : attrs) {
            if (a.spread) throw error(a.line, a.col, "{...spread} is not supported on components; pass props explicitly");
            if (a.name.equals("key")) { key = nl(a.nl) + a.code; continue; }
            if (info == null) {
                throw error(sLine, sCol, "unknown component <" + name + ">: no 'record Props(...)' found in class "
                        + name + " (or it is not in the compiled source folders)");
            }
            if (!info.has(a.name)) {
                throw error(a.line, a.col, "<" + name + "> has no prop '" + a.name + "'"
                        + (info.params().isEmpty() ? "" : "; its props are: " + info.names()));
            }
            if (given.containsKey(a.name)) throw error(a.line, a.col, "duplicate prop '" + a.name + "' on <" + name + ">");
            given.put(a.name, a.code);
            extraNl += a.nl;
        }

        String props;
        if (info == null) {
            if (!children.items().isEmpty()) {
                throw error(sLine, sCol, "unknown component <" + name + ">: no 'record Props(...)' found in class " + name);
            }
            props = "null";
        } else {
            boolean hasChildren = !children.items().isEmpty();
            if (hasChildren && !info.has("children")) {
                throw error(sLine, sCol, "<" + name + "> does not accept children; add 'List<Object> children' to "
                        + name + ".Props");
            }
            if (hasChildren && given.containsKey("children")) {
                throw error(sLine, sCol, "<" + name + "> gets children both as a prop and as nested elements");
            }
            List<String> args = new ArrayList<>();
            for (PropsIndex.Param p : info.params()) {
                if (given.containsKey(p.name())) args.add(given.get(p.name()));
                else if (p.name().equals("children")) args.add(RT + "Jsx.children(" + String.join(", ", children.items()) + ")");
                else args.add(defaultFor(p.type()));
            }
            props = "new " + name + ".Props(" + String.join(", ", args) + ")";
        }
        extraNl += children.trailingNl();
        return RT + "Jsx.c(" + name + ".class, " + name + "::new, " + key + ", " + props + nl(extraNl) + ")";
    }

    private static String joinChildren(Children ch) {
        StringBuilder sb = new StringBuilder();
        for (String item : ch.items()) sb.append(", ").append(item);
        sb.append(nl(ch.trailingNl()));
        return sb.toString();
    }

    private static boolean isEvent(String name) {
        return name.length() > 2 && name.startsWith("on") && Character.isUpperCase(name.charAt(2));
    }

    private static String defaultFor(String type) {
        return switch (type) {
            case "boolean" -> "false";
            case "int" -> "0";
            case "long" -> "0L";
            case "double" -> "0.0";
            case "float" -> "0f";
            case "short" -> "(short) 0";
            case "byte" -> "(byte) 0";
            case "char" -> "'\\0'";
            default -> "null";
        };
    }

    // ------------------------------------------------------------------ JSX text rules (same as React)

    /** React's whitespace rule: trim each line, drop whitespace-only lines, join lines with one space. */
    static String cleanJsxText(String raw) {
        String[] lines = raw.split("\r\n|\n|\r", -1);
        int lastNonEmpty = 0;     // not -1: a single whitespace-only line ("{a} {b}") must keep exactly its one space
        for (int i = 0; i < lines.length; i++) if (!lines[i].isBlank()) lastNonEmpty = i;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].replace('\t', ' ');
            if (i != 0) t = t.replaceAll("^ +", "");
            if (i != lines.length - 1) t = t.replaceAll(" +$", "");
            if (!t.isEmpty()) {
                if (i != lastNonEmpty) t += " ";
                sb.append(t);
            }
        }
        return sb.toString();
    }

    static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int semi = c == '&' ? s.indexOf(';', i) : -1;
            if (semi > i && semi - i <= 10) {
                String ent = s.substring(i + 1, semi);
                String rep = switch (ent) {
                    case "amp" -> "&";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "nbsp" -> " ";
                    case "lbrace" -> "{";
                    case "rbrace" -> "}";
                    case "times" -> "×";
                    case "copy" -> "©";
                    case "hellip" -> "…";
                    case "mdash" -> "—";
                    case "ndash" -> "–";
                    case "middot" -> "·";
                    case "laquo" -> "«";
                    case "raquo" -> "»";
                    case "larr" -> "←";
                    case "rarr" -> "→";
                    default -> {
                        try {
                            if (ent.startsWith("#x")) yield new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
                            if (ent.startsWith("#")) yield new String(Character.toChars(Integer.parseInt(ent.substring(1))));
                        } catch (IllegalArgumentException ignored) { }
                        yield null;
                    }
                };
                if (rep != null) { sb.append(rep); i = semi; continue; }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String javaString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static boolean isBlankJava(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "").isBlank();
    }

    // ------------------------------------------------------------------ low-level scanning

    private String readName(boolean tag) {
        int s = pos;
        if (!Character.isLetter(peek(0)) && peek(0) != '_') throw error(line, col, "expected a " + (tag ? "tag" : "attribute") + " name");
        while (pos < src.length()) {
            char c = peek(0);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '$' || (tag && c == '.')) advance();
            else break;
        }
        return src.substring(s, pos);
    }

    private int skipWs() {
        int n = 0;
        while (pos < src.length() && Character.isWhitespace(peek(0))) {
            if (peek(0) == '\n') n++;
            advance();
        }
        return n;
    }

    private void expect(char c) {
        if (pos >= src.length() || peek(0) != c) {
            String got = pos >= src.length() ? "end of file" : "'" + peek(0) + "'";
            throw error(line, col, "expected '" + c + "' but found " + got);
        }
        advance();
    }

    private char peek(int k) { return pos + k < src.length() ? src.charAt(pos + k) : '\0'; }

    private char advance() {
        char c = src.charAt(pos++);
        if (c == '\n') { line++; col = 1; } else col++;
        return c;
    }

    private void copy() { out.append(advance()); }

    private static String nl(int n) { return n <= 0 ? "" : "\n".repeat(n); }

    private static int countNl(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    private CompileError error(int l, int c, String msg) {
        String[] lines = src.split("\n", -1);
        String srcLine = l - 1 < lines.length ? lines[l - 1] : null;
        return new CompileError(file, l, c, msg, srcLine);
    }
}

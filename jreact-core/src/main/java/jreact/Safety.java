package jreact;

import java.util.Set;

/**
 * Rules that keep untrusted data from turning into script. Used by every renderer (DOM host, server renderer),
 * so the browser and the HTML string output behave the same.
 *
 * <p>What JReact guarantees: text children and attribute values are never parsed as HTML (text nodes and
 * {@code setAttribute} in the browser, escaping in {@code ServerRenderer}); {@code javascript:} URLs are blocked;
 * inline event handler attributes ({@code onclick="..."}) and {@code <script>} elements are refused; tag and
 * attribute names must be plain names. What it does not guard: the content of {@code style} strings,
 * {@code srcdoc} and {@code <iframe>}/{@code <object>} targets, and DOM nodes you reach through a {@code Ref}.
 */
public final class Safety {
    private Safety() {}

    /** Replacement for a blocked URL. */
    public static final String BLOCKED_URL = "about:blank";

    private static final Set<String> URL_ATTRIBUTES = Set.of(
            "href", "src", "action", "formaction", "xlink:href", "poster", "cite", "data", "background",
            "longdesc", "manifest", "codebase", "lowsrc", "dynsrc", "profile", "usemap");

    /** {@code div}, {@code my-element}, {@code svg:rect}: a letter, then letters, digits, {@code -} (one {@code :} allowed). */
    public static boolean isValidTagName(String tag) {
        if (tag == null || tag.isEmpty() || !isAsciiLetter(tag.charAt(0))) return false;
        boolean colon = false;
        for (int i = 1; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (c == ':' && !colon && i < tag.length() - 1) colon = true;
            else if (!(isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '-')) return false;
        }
        return true;
    }

    /** Elements that run code when they are inserted. Set the script in the page's HTML instead. */
    public static boolean isBlockedTag(String tag) { return "script".equalsIgnoreCase(tag); }

    /** An attribute name that cannot break out of a tag or make {@code setAttribute} throw. */
    public static boolean isValidAttributeName(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!(isAsciiLetter(first) || first == '_' || first == ':')) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_' || c == ':' || c == '.' || c == '-')) return false;
        }
        return true;
    }

    /** {@code onclick}, {@code onError}...: handlers are passed as functions ({@code onClick={e -> ...}}), never as code strings. */
    public static boolean isInlineHandlerAttribute(String name) {
        return name.length() > 2 && (name.charAt(0) == 'o' || name.charAt(0) == 'O')
                && (name.charAt(1) == 'n' || name.charAt(1) == 'N');
    }

    public static boolean isUrlAttribute(String name) {
        return URL_ATTRIBUTES.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Whether a browser would run this URL as script. Browsers ignore control characters, tabs and newlines
     * inside the scheme ({@code "java\tscript:..."}), so those are skipped here too.
     */
    public static boolean isDangerousUrl(Object value) {
        if (value == null) return false;
        String s = String.valueOf(value);
        StringBuilder head = new StringBuilder(12);
        for (int i = 0; i < s.length() && head.length() < 11; i++) {
            char c = s.charAt(i);
            if (c <= ' ') continue;
            head.append(Character.toLowerCase(c));
        }
        String h = head.toString();
        return h.startsWith("javascript:") || h.startsWith("vbscript:");
    }

    private static boolean isAsciiLetter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
}

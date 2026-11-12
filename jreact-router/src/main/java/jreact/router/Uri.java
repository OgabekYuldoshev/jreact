package jreact.router;

/** Percent-encoding of URL parts (UTF-8), without java.net so it also runs in the browser build. */
final class Uri {
    private Uri() {}

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** {@code %20} to a space (and {@code +} to a space when {@code plusIsSpace}). Broken escapes stay as they are. */
    static String decode(String s, boolean plusIsSpace) {
        if (s.indexOf('%') < 0 && !(plusIsSpace && s.indexOf('+') >= 0)) return s;
        // collect the bytes, then read them as UTF-8 by hand (java.nio charsets would pull ~50 KB into the browser bundle)
        int[] bytes = new int[s.length() * 4];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                bytes[n++] = hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2));
                i += 2;
            } else if (c == '+' && plusIsSpace) {
                bytes[n++] = ' ';
            } else {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp) - 1;
                n = utf8(cp, bytes, n);
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; ) {
            int b = bytes[i];
            int len = b < 0x80 ? 1 : b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC2 ? 2 : 0;
            boolean ok = len > 0 && i + len <= n;
            for (int k = 1; ok && k < len; k++) ok = (bytes[i + k] & 0xC0) == 0x80;
            if (!ok) { out.append('\uFFFD'); i++; continue; }
            int cp = len == 1 ? b : len == 2 ? b & 0x1F : len == 3 ? b & 0x0F : b & 0x07;
            for (int k = 1; k < len; k++) cp = (cp << 6) | (bytes[i + k] & 0x3F);
            if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) out.append('\uFFFD'); else out.appendCodePoint(cp);
            i += len;
        }
        return out.toString();
    }

    private static int utf8(int cp, int[] out, int n) {
        if (cp < 0x80) out[n++] = cp;
        else if (cp < 0x800) { out[n++] = 0xC0 | (cp >> 6); out[n++] = 0x80 | (cp & 0x3F); }
        else if (cp < 0x10000) { out[n++] = 0xE0 | (cp >> 12); out[n++] = 0x80 | ((cp >> 6) & 0x3F); out[n++] = 0x80 | (cp & 0x3F); }
        else { out[n++] = 0xF0 | (cp >> 18); out[n++] = 0x80 | ((cp >> 12) & 0x3F); out[n++] = 0x80 | ((cp >> 6) & 0x3F); out[n++] = 0x80 | (cp & 0x3F); }
        return n;
    }

    /** Encodes for a query string: letters, digits and {@code -_.~} stay, a space is {@code +}, the rest is {@code %XX}. */
    static String encodeQuery(String s) {
        StringBuilder sb = new StringBuilder();
        int[] buf = new int[4];
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0xD800 && cp <= 0xDFFF) cp = 0xFFFD;           // a lone surrogate (UTF-8 cannot hold it)
            int len = utf8(cp, buf, 0);
            for (int k = 0; k < len; k++) {
                int c = buf[k];
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') sb.append((char) c);
                else if (c == ' ') sb.append('+');
                else sb.append('%').append(HEX[c >> 4]).append(HEX[c & 15]);
            }
        }
        return sb.toString();
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}

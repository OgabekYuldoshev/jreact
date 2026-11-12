package jreact.router;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Percent-encoding of URL parts (UTF-8), without java.net so it also runs in the browser build. */
final class Uri {
    private Uri() {}

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** {@code %20} to a space (and {@code +} to a space when {@code plusIsSpace}). Broken escapes stay as they are. */
    static String decode(String s, boolean plusIsSpace) {
        if (s.indexOf('%') < 0 && !(plusIsSpace && s.indexOf('+') >= 0)) return s;
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                out.write(hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2)));
                i += 2;
            } else if (c == '+' && plusIsSpace) {
                out.write(' ');
            } else {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp) - 1;
                byte[] b = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                out.write(b, 0, b.length);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Encodes for a query string: letters, digits and {@code -_.~} stay, a space is {@code +}, the rest is {@code %XX}. */
    static String encodeQuery(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') sb.append((char) c);
            else if (c == ' ') sb.append('+');
            else sb.append('%').append(HEX[c >> 4]).append(HEX[c & 15]);
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

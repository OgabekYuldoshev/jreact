package dev.jreact.spring;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of index.html, adapted to where the app is served: with a servlet context path ({@code server.servlet.context-path=/shop})
 * the root-absolute URLs in the page get that prefix, and {@code <meta name="jreact-base" content="/shop">} tells the browser side
 * (router basename, RPC and {@code Http} URLs). In dev mode the file is read on every request and gets the reload script.
 */
final class IndexHtml {

    private static final Pattern ROOT_URL = Pattern.compile("(\\b(?:href|src)=)([\"'])/(?!/)");
    private static final Pattern HEAD = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE);

    private final Resource index;
    private final boolean dev;
    private String cached;

    private final String loginUrl;

    IndexHtml(Resource index, boolean dev) { this(index, dev, null); }

    IndexHtml(Resource index, boolean dev, String loginUrl) {
        this.index = index;
        this.dev = dev;
        this.loginUrl = loginUrl == null || loginUrl.isBlank() ? null : loginUrl;
    }

    String html(String contextPath) throws IOException {
        String html = cached;
        if (html == null) {
            html = new String(index.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!dev) cached = html;
        }
        String out = adapt(html, contextPath == null ? "" : contextPath);
        if (loginUrl != null) out = withMeta(out, "jreact-login", loginUrl);
        return out;
    }

    /** {@code <meta name="jreact-login" content="/login">}: the browser side goes there when a call answers 401. */
    private static String withMeta(String html, String name, String content) {
        String meta = "<meta name=\"" + name + "\" content=\"" + content.replace("&", "&amp;").replace("\"", "&quot;") + "\">";
        Matcher head = HEAD.matcher(html);
        return head.find() ? html.substring(0, head.end()) + meta + html.substring(head.end()) : meta + html;
    }

    static String adapt(String html, String contextPath) {
        String out = html;
        if (!contextPath.isEmpty()) {
            out = ROOT_URL.matcher(out).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + m.group(2) + contextPath + "/"));
            String meta = "<meta name=\"jreact-base\" content=\"" + contextPath.replace("&", "&amp;").replace("\"", "&quot;") + "\">";
            Matcher head = HEAD.matcher(out);
            out = head.find() ? out.substring(0, head.end()) + meta + out.substring(head.end()) : meta + out;
        }
        return out;
    }

    /** Dev mode: the reload script just before the end of the page. */
    String withReload(String html, String contextPath) {
        String tag = "<script src=\"" + contextPath + "/__jreact/reload.js\"></script>";
        int end = html.lastIndexOf("</body>");
        return end < 0 ? html + tag : html.substring(0, end) + tag + html.substring(end);
    }
}

package jreact.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Turns the TeaVM output into what a server serves (shared by the Maven goal {@code jrx:bundle} and the Gradle task {@code jrxBundle}).
 * {@code app.js} and {@code app.css} get a content hash in their name ({@code assets/app.3f9a1c2b.js}, cacheable forever),
 * {@code index.html} is rewritten to point at them, a gzip copy is written next to each, and {@code manifest.json} lists the result.
 * Every other file of the web folder is copied as is.
 * <p>There is no need to write an {@code index.html}: without one in the web folder the page is generated from a {@link Page}
 * (title, language, html/body/root classes, extra {@code <head>} HTML, plus the optional fragment file {@code head.html} of the web folder).
 * An {@code index.html} of your own is still used as is (only the asset paths are rewritten).
 */
public final class Bundler {
    private Bundler() {}

    /**
     * What the generated {@code index.html} says. {@code head} is raw HTML put at the end of {@code <head>} (analytics, Open Graph tags, fonts);
     * a file {@code head.html} in the web folder (src/main/webapp) is added after it.
     */
    public record Page(String title, String lang, String htmlClass, String bodyClass, String rootClass, String head) {
        public static final Page DEFAULT = new Page("App", "en", "", "", "", "");

        public Page {
            title = title == null || title.isBlank() ? "App" : title;
            lang = lang == null || lang.isBlank() ? "en" : lang;
            htmlClass = htmlClass == null ? "" : htmlClass;
            bodyClass = bodyClass == null ? "" : bodyClass;
            rootClass = rootClass == null ? "" : rootClass;
            head = head == null ? "" : head;
        }
    }

    /** @param jsName e.g. {@code assets/app.3f9a1c2b.js}; {@code message}: one line about what was written */
    public record Result(boolean ran, String jsName, String cssName, String message) {}

    /**
     * @param web        the TeaVM result: app.js, index.html and the other files of src/main/webapp
     * @param cssFile    compiled CSS, or null/missing to use {@code app.css} of the web folder if there is one
     * @param out        output folder (deleted first)
     * @param publicPath URL prefix of the assets in index.html
     * @param warn       receives warnings
     */
    public static Result bundle(Path web, Path cssFile, Path out, boolean hash, boolean compress, String publicPath, Consumer<String> warn) throws IOException {
        return bundle(web, cssFile, out, hash, compress, publicPath, Page.DEFAULT, warn);
    }

    /** As above, with the {@code page} the generated index.html is made from (ignored when the web folder has an index.html). */
    public static Result bundle(Path web, Path cssFile, Path out, boolean hash, boolean compress, String publicPath, Page page, Consumer<String> warn) throws IOException {
        Path js = web.resolve("app.js");
        if (!Files.isRegularFile(js)) return new Result(false, null, null, "no app.js in " + web + ", nothing to do");
        Path css = cssFile != null && Files.isRegularFile(cssFile) ? cssFile : (Files.isRegularFile(web.resolve("app.css")) ? web.resolve("app.css") : null);
        String prefix = publicPath.endsWith("/") ? publicPath : publicPath + "/";
        deleteRecursively(out);
        Files.createDirectories(out.resolve("assets"));
        // everything else first (favicon, images, robots.txt); app.js, app.css and index.html are handled below
        try (Stream<Path> walk = Files.walk(web)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String rel = web.relativize(p).toString().replace(File.separatorChar, '/');
                if (rel.equals("app.js") || rel.equals("app.css") || rel.equals("index.html") || rel.equals("head.html")) continue;
                copy(p, out.resolve(rel));
            }
        }
        String jsName = emit(js, "app", "js", out, hash, compress);
        String cssName = css == null ? null : emit(css, "app", "css", out, hash, compress);
        if (css == null) warn.accept("no CSS found (looked for " + cssFile + " and app.css). Run `jreact build` for Tailwind, or add src/main/webapp/app.css");

        Path index = web.resolve("index.html");
        String html;
        if (Files.isRegularFile(index)) {
            html = Files.readString(index, StandardCharsets.UTF_8);
            html = html.replace("\"app.js\"", "\"" + prefix + jsName + "\"").replace("\"./app.js\"", "\"" + prefix + jsName + "\"");
            if (cssName != null) html = html.replace("\"app.css\"", "\"" + prefix + cssName + "\"").replace("\"./app.css\"", "\"" + prefix + cssName + "\"");
        } else {
            Path headFile = web.resolve("head.html");
            String fragment = Files.isRegularFile(headFile) ? Files.readString(headFile, StandardCharsets.UTF_8) : "";
            html = generate(page, prefix, jsName, cssName, icon(web), fragment);
        }
        Path target = out.resolve("index.html");
        Files.writeString(target, html, StandardCharsets.UTF_8);
        if (compress) gzip(target);

        StringBuilder manifest = new StringBuilder("{\n  \"app.js\": \"").append(jsName).append('"');
        if (cssName != null) manifest.append(",\n  \"app.css\": \"").append(cssName).append('"');
        manifest.append("\n}\n");
        Files.writeString(out.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
        String message = jsName + " (" + Files.size(out.resolve(jsName)) / 1024 + " KB"
                + (compress ? ", " + Files.size(out.resolve(jsName + ".gz")) / 1024 + " KB gzip" : "") + ")"
                + (cssName == null ? "" : ", " + cssName) + " -> " + out;
        return new Result(true, jsName, cssName, message);
    }

    /** The favicon in the web folder, if any (relative name). */
    private static String icon(Path web) {
        for (String name : List.of("favicon.svg", "favicon.ico", "favicon.png")) if (Files.isRegularFile(web.resolve(name))) return name;
        return null;
    }

    /** The page the framework needs: the stylesheet, the root element (id {@code root}, what {@code createRoot("root")} and the server side rendering look for), app.js and the call of {@code main()}. */
    static String generate(Page page, String prefix, String jsName, String cssName, String icon, String headFragment) {
        StringBuilder h = new StringBuilder("<!doctype html>\n<html lang=\"").append(attr(page.lang())).append("\"");
        if (!page.htmlClass().isEmpty()) h.append(" class=\"").append(attr(page.htmlClass())).append("\"");
        h.append(">\n<head>\n    <meta charset=\"utf-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        h.append("    <title>").append(text(page.title())).append("</title>\n");
        if (icon != null) h.append("    <link rel=\"icon\" href=\"").append(attr(prefix + icon)).append("\">\n");
        if (cssName != null) h.append("    <link rel=\"stylesheet\" href=\"").append(prefix).append(cssName).append("\">\n");
        for (String extra : new String[] { page.head(), headFragment }) {
            if (extra.isBlank()) continue;
            for (String line : extra.strip().split("\\R")) h.append("    ").append(line.strip()).append('\n');
        }
        h.append("</head>\n<body");
        if (!page.bodyClass().isEmpty()) h.append(" class=\"").append(attr(page.bodyClass())).append("\"");
        h.append(">\n    <div id=\"root\"");
        if (!page.rootClass().isEmpty()) h.append(" class=\"").append(attr(page.rootClass())).append("\"");
        h.append("></div>\n    <script src=\"").append(prefix).append(jsName).append("\"></script>\n    <script>main();</script>\n</body>\n</html>\n");
        return h.toString();
    }

    private static String text(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private static String attr(String s) { return text(s).replace("\"", "&quot;"); }

    /** Copies {@code source} to {@code assets/<base>[.<hash>].<ext>} (and a .gz), returns the path relative to the output folder. */
    private static String emit(Path source, String base, String ext, Path out, boolean hash, boolean compress) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        String name = "assets/" + base + (hash ? "." + sha256(bytes).substring(0, 10) : "") + "." + ext;
        Path target = out.resolve(name);
        Files.write(target, bytes);
        if (compress) gzip(target);
        return name;
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.copy(from, to);
    }

    private static void gzip(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (OutputStream gz = new GZIPOutputStream(buf)) { gz.write(bytes); }
        Files.write(file.resolveSibling(file.getFileName() + ".gz"), buf.toByteArray());
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        List<Path> all = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) { walk.forEach(all::add); }
        for (int i = all.size() - 1; i >= 0; i--) Files.delete(all.get(i));
    }
}

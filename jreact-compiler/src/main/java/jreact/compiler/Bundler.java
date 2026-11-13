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
 */
public final class Bundler {
    private Bundler() {}

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
                if (rel.equals("app.js") || rel.equals("app.css") || rel.equals("index.html")) continue;
                copy(p, out.resolve(rel));
            }
        }
        String jsName = emit(js, "app", "js", out, hash, compress);
        String cssName = css == null ? null : emit(css, "app", "css", out, hash, compress);
        if (css == null) warn.accept("no CSS found (looked for " + cssFile + " and app.css). Run `jreact build` for Tailwind, or add src/main/webapp/app.css");

        Path index = web.resolve("index.html");
        if (Files.isRegularFile(index)) {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            html = html.replace("\"app.js\"", "\"" + prefix + jsName + "\"").replace("\"./app.js\"", "\"" + prefix + jsName + "\"");
            if (cssName != null) html = html.replace("\"app.css\"", "\"" + prefix + cssName + "\"").replace("\"./app.css\"", "\"" + prefix + cssName + "\"");
            Path target = out.resolve("index.html");
            Files.writeString(target, html, StandardCharsets.UTF_8);
            if (compress) gzip(target);
        } else warn.accept("no index.html in " + web);

        StringBuilder manifest = new StringBuilder("{\n  \"app.js\": \"").append(jsName).append('"');
        if (cssName != null) manifest.append(",\n  \"app.css\": \"").append(cssName).append('"');
        manifest.append("\n}\n");
        Files.writeString(out.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
        String message = jsName + " (" + Files.size(out.resolve(jsName)) / 1024 + " KB"
                + (compress ? ", " + Files.size(out.resolve(jsName + ".gz")) / 1024 + " KB gzip" : "") + ")"
                + (cssName == null ? "" : ", " + cssName) + " -> " + out;
        return new Result(true, jsName, cssName, message);
    }

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

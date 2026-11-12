package jreact.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * {@code jrx:bundle}: turns the TeaVM output into what a server serves. {@code app.js} and {@code app.css} get a content hash in
 * their name ({@code assets/app.3f9a1c2b.js}, cacheable forever), {@code index.html} is rewritten to point at them, a gzip copy is
 * written next to each, and {@code manifest.json} lists the result. Every other file of the web folder is copied as is.
 * The output goes to {@code target/classes/jreact} so that it lands in the module's jar and a Spring Boot server finds it on its classpath.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class BundleMojo extends AbstractMojo {

    /** The TeaVM result: app.js, index.html and the other files of src/main/webapp. */
    @Parameter(defaultValue = "${project.build.directory}/web", property = "jreact.webDirectory")
    private File webDirectory;

    /** Compiled CSS (what the jreact CLI's Tailwind step writes). When missing, {@code app.css} of the web folder is used if there is one. */
    @Parameter(defaultValue = "${project.build.directory}/tailwind/app.css", property = "jreact.cssFile")
    private File cssFile;

    @Parameter(defaultValue = "${project.build.outputDirectory}/jreact", property = "jreact.outputDirectory")
    private File outputDirectory;

    /** Put a content hash in the file names. Off in dev, so the names stay the same between rebuilds. */
    @Parameter(defaultValue = "true", property = "jreact.hash")
    private boolean hash;

    /** Write .gz copies of js, css and html. */
    @Parameter(defaultValue = "true", property = "jreact.compress")
    private boolean compress;

    /** URL prefix of the assets in index.html. */
    @Parameter(defaultValue = "/", property = "jreact.publicPath")
    private String publicPath;

    @Override
    public void execute() throws MojoExecutionException {
        Path web = webDirectory.toPath();
        Path js = web.resolve("app.js");
        if (!Files.isRegularFile(js)) { getLog().info("jrx:bundle: no app.js in " + web + ", nothing to do"); return; }
        Path css = cssFile != null && cssFile.isFile() ? cssFile.toPath() : (Files.isRegularFile(web.resolve("app.css")) ? web.resolve("app.css") : null);
        Path out = outputDirectory.toPath();
        String prefix = publicPath.endsWith("/") ? publicPath : publicPath + "/";
        try {
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
            String jsName = emit(js, "app", "js", out);
            String cssName = css == null ? null : emit(css, "app", "css", out);
            if (css == null) getLog().warn("jrx:bundle: no CSS found (looked for " + cssFile + " and app.css). Run `jreact build` for Tailwind, or add src/main/webapp/app.css");

            Path index = web.resolve("index.html");
            if (Files.isRegularFile(index)) {
                String html = Files.readString(index, StandardCharsets.UTF_8);
                html = html.replace("\"app.js\"", "\"" + prefix + jsName + "\"").replace("\"./app.js\"", "\"" + prefix + jsName + "\"");
                if (cssName != null) html = html.replace("\"app.css\"", "\"" + prefix + cssName + "\"").replace("\"./app.css\"", "\"" + prefix + cssName + "\"");
                Path target = out.resolve("index.html");
                Files.writeString(target, html, StandardCharsets.UTF_8);
                if (compress) gzip(target);
            } else getLog().warn("jrx:bundle: no index.html in " + web);

            StringBuilder manifest = new StringBuilder("{\n  \"app.js\": \"").append(jsName).append('"');
            if (cssName != null) manifest.append(",\n  \"app.css\": \"").append(cssName).append('"');
            manifest.append("\n}\n");
            Files.writeString(out.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
            getLog().info("jrx:bundle: " + jsName + " (" + Files.size(out.resolve(jsName)) / 1024 + " KB"
                    + (compress ? ", " + Files.size(out.resolve(jsName + ".gz")) / 1024 + " KB gzip" : "") + ")"
                    + (cssName == null ? "" : ", " + cssName) + " -> " + out);
        } catch (IOException e) {
            throw new MojoExecutionException("jrx:bundle failed: " + e.getMessage(), e);
        }
    }

    /** Copies {@code source} to {@code assets/<base>[.<hash>].<ext>} (and a .gz), returns the path relative to the output folder. */
    private String emit(Path source, String base, String ext, Path out) throws IOException {
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

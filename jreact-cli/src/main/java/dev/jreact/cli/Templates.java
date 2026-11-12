package dev.jreact.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Writes a new project from the templates inside the jar ({@code templates/spring}, listed in its index.txt). */
final class Templates {
    private Templates() {}

    private static final String ROOT = "/templates/spring/";
    static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");

    record Params(String name, String group, String pkg, Versions versions) {}

    /** The files a new project consists of (relative to the project folder, after name mapping). */
    static List<String> files() {
        List<String> out = new ArrayList<>();
        for (String line : read("index.txt").split("\n")) if (!line.isBlank()) out.add(line.strip());
        return out;
    }

    /** Creates {@code target} (must not exist or be empty) and returns the written files. */
    static List<Path> generate(Path target, Params p) throws IOException {
        if (Files.exists(target) && !isEmptyDir(target)) throw new IOException(target + " already exists and is not empty");
        List<Path> written = new ArrayList<>();
        for (String file : files()) {
            Path out = target.resolve(mapPath(file, p));
            Files.createDirectories(out.getParent());
            Files.writeString(out, render(read(file), p), StandardCharsets.UTF_8);
            written.add(out);
        }
        return written;
    }

    static String mapPath(String file, Params p) {
        String mapped = file.replace("__pkg__", p.pkg().replace('.', '/'));
        return mapped.equals("_gitignore") ? ".gitignore" : mapped;
    }

    static String render(String text, Params p) {
        return text.replace("{{name}}", p.name()).replace("{{group}}", p.group()).replace("{{package}}", p.pkg())
                .replace("{{jreactVersion}}", p.versions().jreact()).replace("{{springBootVersion}}", p.versions().springBoot())
                .replace("{{teavmVersion}}", p.versions().teavm());
    }

    private static String read(String file) {
        try (InputStream in = Templates.class.getResourceAsStream(ROOT + file)) {
            if (in == null) throw new IllegalStateException("template file missing from the CLI jar: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isEmptyDir(Path p) throws IOException {
        if (!Files.isDirectory(p)) return false;
        try (var s = Files.list(p)) { return s.findAny().isEmpty(); }
    }
}

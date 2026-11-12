package dev.jreact.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** A JReact project on disk: the folder with jreact.properties (shared/, client/, server/ next to it). */
record Project(Path root, String shared, String client, String server, int port) {

    static final String MARKER = "jreact.properties";

    /** Looks for the marker in {@code from} and its parents. */
    static Project find(Path from) {
        for (Path dir = from.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(MARKER))) return load(dir);
        }
        throw new IllegalStateException("not inside a JReact project (no " + MARKER + " in " + from + " or above). Create one with: jreact new <name>");
    }

    static Project load(Path root) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(root.resolve(MARKER))) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + root.resolve(MARKER) + ": " + e.getMessage(), e);
        }
        return new Project(root, p.getProperty("shared", "shared"), p.getProperty("client", "client"), p.getProperty("server", "server"),
                Integer.parseInt(p.getProperty("port", "8080").strip()));
    }

    Path sharedDir() { return root.resolve(shared); }
    Path clientDir() { return root.resolve(client); }
    Path serverDir() { return root.resolve(server); }

    /** Where {@code jrx:bundle} writes in dev (unhashed): the server serves straight from here. */
    Path devDir() { return clientDir().resolve("target/classes/jreact"); }

    /** The CLI writes the build error here; the page shows it. */
    Path errorFile() { return clientDir().resolve("target/jreact-error.txt"); }

    Path cssInput() { return clientDir().resolve("src/main/styles/app.css"); }
    Path cssOutput() { return clientDir().resolve("target/tailwind/app.css"); }
}

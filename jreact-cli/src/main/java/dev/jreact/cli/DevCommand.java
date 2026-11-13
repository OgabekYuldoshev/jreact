package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * {@code jreact dev}: the Spring Boot server (devtools) plus a watcher.
 * <ul>
 *   <li>client/ changes: Tailwind + {@code mvn process-classes} (Gradle: {@code jrxBundle}) (jrx -> java -> TeaVM, unoptimized), the server sees the new files, the page reloads</li>
 *   <li>server/ changes: {@code mvn compile} (Gradle: {@code classes}), devtools restarts Spring</li>
 *   <li>shared/ changes: both sides are rebuilt and the server is restarted</li>
 * </ul>
 * A failed build is shown in the browser (an overlay) and in the terminal, and the last good UI keeps running.
 */
@Command(name = "dev", description = "Runs the server and rebuilds on every change; the browser reloads by itself.")
final class DevCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "port (default: port in jreact.properties)")
    Integer port;

    private Project project;
    private BuildTool tool;
    private Tailwind tailwind;
    private Process server;
    private int serverPort;

    @Override
    public Integer call() throws Exception {
        project = Project.find(Path.of("."));
        tool = BuildTool.of(project);
        serverPort = port != null ? port : project.port();
        tailwind = Tailwind.ensure(Versions.load());

        Ui.step("first build (the browser build takes a little while)");
        Files.deleteIfExists(project.errorFile());
        String failure = buildAll();
        if (failure != null) { Ui.fail("the first build failed:\n" + failure); return 1; }
        Ui.ok("built");

        startServer();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
        Ui.info("");
        Ui.ok("http://localhost:" + serverPort + "  " + Ui.dim("(the server needs a few seconds to start; changes reload the page by themselves, Ctrl+C stops)"));
        Ui.info("");

        Map<Path, Long> shared = scan(project.sharedDir()), client = scan(project.clientDir()), serverSrc = scan(project.serverDir());
        Map<Path, Long> poms = scanBuildFiles();
        while (true) {
            Thread.sleep(400);
            if (!server.isAlive()) { Ui.fail("the server stopped (exit " + server.exitValue() + ")"); return server.exitValue() == 0 ? 0 : 1; }
            Map<Path, Long> nowShared = scan(project.sharedDir()), nowClient = scan(project.clientDir()), nowServer = scan(project.serverDir());
            Map<Path, Long> nowPoms = scanBuildFiles();
            boolean sharedChanged = !nowShared.equals(shared), clientChanged = !nowClient.equals(client), serverChanged = !nowServer.equals(serverSrc);
            boolean pomChanged = !nowPoms.equals(poms);
            shared = nowShared; client = nowClient; serverSrc = nowServer; poms = nowPoms;
            if (!sharedChanged && !clientChanged && !serverChanged && !pomChanged) continue;
            Thread.sleep(150);          // an editor saves several files: let it finish

            if (pomChanged) Ui.warn("a build file (pom.xml / build.gradle) changed: stop (Ctrl+C) and run `jreact dev` again to pick it up");
            if (sharedChanged) {
                long t0 = System.nanoTime();
                String error = buildAll();
                if (error == null) { restartServer(); Ui.ok("shared changed: rebuilt, server restarted " + secs(t0)); }
                else report("shared", error);
            } else {
                if (clientChanged) {
                    long t0 = System.nanoTime();
                    String error = buildClient();
                    if (error == null) { clearError(); Ui.ok("client rebuilt " + secs(t0)); } else report("client", error);
                }
                if (serverChanged) {
                    long t0 = System.nanoTime();
                    BuildTool.Result r = tool.capture(tool.compileServer(), Map.of());
                    if (r.ok()) { clearError(); Ui.ok("server compiled, Spring restarts " + secs(t0)); } else report("server", r.output());
                }
            }
            // sources moved while we were building: take a new snapshot so our own output does not retrigger
            shared = scan(project.sharedDir()); client = scan(project.clientDir()); serverSrc = scan(project.serverDir());
        }
    }

    // ---------------------------------------------------------------- builds

    /** Tailwind, then shared + client into the local repository (the server module resolves them from there). Returns the error, or null. */
    private String buildAll() throws IOException, InterruptedException {
        String css = css();
        if (css != null) { writeError(css); return css; }
        BuildTool.Result r = tool.capture(tool.buildAll(), Map.of());
        if (!r.ok()) { writeError(BuildTool.tail(r.output(), 60)); return BuildTool.tail(r.output(), 60); }
        clearError();
        return null;
    }

    private String buildClient() throws IOException, InterruptedException {
        String css = css();
        if (css != null) return css;
        BuildTool.Result r = tool.capture(tool.buildClient(), Map.of());
        return r.ok() ? null : BuildTool.tail(r.output(), 60);
    }

    private String css() throws IOException, InterruptedException {
        return tailwind == null ? null : tailwind.build(project, false);
    }

    // ---------------------------------------------------------------- the server

    private void startServer() throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("JREACT_DEVDIR", project.devDir().toString());              // serve the UI straight from the client's output
        env.put("JREACT_DEVERRORFILE", project.errorFile().toString());
        env.put("SERVER_PORT", String.valueOf(serverPort));
        server = tool.start(tool.runServer(), env);
    }

    private void restartServer() throws IOException, InterruptedException {
        stopServer();
        startServer();
    }

    private void stopServer() {
        if (server == null) return;
        server.descendants().forEach(ProcessHandle::destroy);
        server.destroy();
        try { server.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---------------------------------------------------------------- errors

    private void report(String what, String error) {
        Ui.fail(what + " build failed:\n" + error);
        writeError(what + " build failed\n\n" + error);
    }

    private void writeError(String text) {
        try {
            Files.createDirectories(project.errorFile().getParent());
            Files.writeString(project.errorFile(), text, StandardCharsets.UTF_8);
        } catch (IOException e) { Ui.warn("cannot write " + project.errorFile() + ": " + e.getMessage()); }
    }

    private void clearError() {
        try { Files.deleteIfExists(project.errorFile()); } catch (IOException ignored) { /* the page just keeps showing it */ }
    }

    private static String secs(long startNanos) { return Ui.dim("(" + Math.round((System.nanoTime() - startNanos) / 1e8) / 10.0 + "s)"); }

    // ---------------------------------------------------------------- watching: a poll of mtimes and sizes (WatchService is slow on macOS)

    private Map<Path, Long> scanBuildFiles() {
        Map<Path, Long> out = new HashMap<>();
        for (Path p : tool.buildFiles()) stamp(p, out);
        return out;
    }

    static Map<Path, Long> scan(Path module) {
        Map<Path, Long> out = new HashMap<>();
        Path src = module.resolve("src");
        if (!Files.isDirectory(src)) return out;
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(Files::isRegularFile).filter(f -> !ignored(f)).forEach(f -> stamp(f, out));
        } catch (IOException e) { /* a file vanished while editing: the next poll sees the final state */ }
        return out;
    }

    private static void stamp(Path f, Map<Path, Long> out) {
        try { out.put(f, Files.getLastModifiedTime(f).toMillis() * 31 + Files.size(f)); } catch (IOException e) { /* gone */ }
    }

    private static boolean ignored(Path f) {
        String n = f.getFileName().toString();
        return n.endsWith("~") || n.endsWith(".swp") || n.endsWith(".tmp") || n.startsWith(".#") || n.equals(".DS_Store") || n.startsWith("4913");
    }
}

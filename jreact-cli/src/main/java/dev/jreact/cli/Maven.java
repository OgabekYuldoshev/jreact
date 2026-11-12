package dev.jreact.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runs Maven in the project: ./mvnw when the project has one, else mvn from the PATH. */
final class Maven {
    private Maven() {}

    /** Dev builds: no optimization, no hashing, no gzip. Fast, and the file names do not change between rebuilds. */
    static final List<String> DEV_PROPS = List.of("-Djreact.optimization=SIMPLE", "-Djreact.minify=false", "-Djreact.hash=false", "-Djreact.compress=false");

    record Result(int exitCode, String output) {
        boolean ok() { return exitCode == 0; }
    }

    static boolean windows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    static String executable(Project p) {
        Path wrapper = p.root().resolve(windows() ? "mvnw.cmd" : "mvnw");
        if (Files.isRegularFile(wrapper) && (windows() || Files.isExecutable(wrapper))) return wrapper.toString();
        return windows() ? "mvn.cmd" : "mvn";
    }

    static List<String> command(Project p, List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable(p));
        cmd.add("-B");
        cmd.addAll(args);
        return cmd;
    }

    /** Runs and captures everything the build prints (stdout and stderr together). */
    static Result capture(Project p, List<String> args, Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command(p, args)).directory(p.root().toFile()).redirectErrorStream(true);
        pb.environment().putAll(env);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(proc.waitFor(), out);
    }

    /** Runs with the build's output going straight to the terminal. */
    static int inherit(Project p, List<String> args, Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command(p, args)).directory(p.root().toFile()).inheritIO();
        pb.environment().putAll(env);
        return pb.start().waitFor();
    }

    static Process start(Project p, List<String> args, Map<String, String> env) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command(p, args)).directory(p.root().toFile()).inheritIO();
        pb.environment().putAll(env);
        return pb.start();
    }

    /** The last lines of a build log (the part that says what failed). */
    static String tail(String output, int lines) {
        String[] all = output.strip().split("\n");
        int from = Math.max(0, all.length - lines);
        return String.join("\n", java.util.Arrays.copyOfRange(all, from, all.length));
    }
}

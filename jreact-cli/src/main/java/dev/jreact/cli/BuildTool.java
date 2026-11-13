package dev.jreact.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The build tool of a project (Maven or Gradle): how to run it, and what its command lines are for the steps the CLI needs.
 * The CLI does the same things for both: build the UI (dev: fast, unhashed), compile the server, run it, run the tests, package the jar.
 */
abstract class BuildTool {

    record Result(int exitCode, String output) {
        boolean ok() { return exitCode == 0; }
    }

    static BuildTool of(Project project) {
        return project.gradle() ? new Gradle(project) : new Maven(project);
    }

    protected final Project project;

    BuildTool(Project project) { this.project = project; }

    // ---------------------------------------------------------------- what the commands ask for

    abstract String name();

    /** Dev: shared and client built, unoptimized, ready for the server to pick up. */
    abstract List<String> buildAll();

    /** Dev: only the client (jrx -> java -> TeaVM -> bundle). */
    abstract List<String> buildClient();

    abstract List<String> compileServer();

    /** Dev: runs the Spring Boot server (devtools restarts it when its classes change). */
    abstract List<String> runServer();

    abstract List<String> packageJar(boolean skipTests, List<String> extra);

    abstract List<String> test(List<String> extra);

    /** The module build files that are watched in dev (a change needs a restart of `jreact dev`). */
    abstract List<Path> buildFiles();

    /** The executable and its fixed leading arguments. */
    abstract List<String> base();

    /** Where the runnable server jar ends up. */
    abstract Path jarDirectory();

    /** Version line for `jreact doctor`. */
    abstract List<String> versionArgs();

    // ---------------------------------------------------------------- running

    static boolean windows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    /** ./mvnw or ./gradlew when the project has one, else the tool from the PATH. */
    static String executable(Project p, String wrapper, String global) {
        Path w = p.root().resolve(windows() ? wrapper + ".cmd" : wrapper);
        if (Files.isRegularFile(w) && (windows() || Files.isExecutable(w))) return w.toString();
        return windows() ? global + (global.equals("mvn") ? ".cmd" : ".bat") : global;
    }

    private List<String> command(List<String> args) {
        List<String> cmd = new ArrayList<>(base());
        cmd.addAll(args);
        return cmd;
    }

    /** Runs and captures everything the build prints (stdout and stderr together). */
    Result capture(List<String> args, Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command(args)).directory(project.root().toFile()).redirectErrorStream(true);
        pb.environment().putAll(env);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(proc.waitFor(), out);
    }

    /** Runs with the build's output going straight to the terminal. */
    int inherit(List<String> args, Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command(args)).directory(project.root().toFile()).inheritIO();
        pb.environment().putAll(env);
        return pb.start().waitFor();
    }

    Process start(List<String> args, Map<String, String> env) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command(args)).directory(project.root().toFile()).inheritIO();
        pb.environment().putAll(env);
        return pb.start();
    }

    /** The last lines of a build log (the part that says what failed). */
    static String tail(String output, int lines) {
        String[] all = output.strip().split("\n");
        int from = Math.max(0, all.length - lines);
        return String.join("\n", Arrays.copyOfRange(all, from, all.length));
    }
}

package dev.jreact.cli;

import picocli.CommandLine.Command;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Checks that Java, Maven or Gradle and the tools the other commands need are in place.")
final class DoctorCommand implements Callable<Integer> {

    private boolean bad;

    @Override
    public Integer call() throws Exception {
        Versions v = Versions.load();
        int javaVersion = Runtime.version().feature();
        if (javaVersion >= 17) Ui.ok("Java " + javaVersion + " (17 or newer needed)");
        else problem("Java " + javaVersion + ": JReact needs 17 or newer");

        Project project = null;
        try { project = Project.find(Path.of(".")); } catch (IllegalStateException e) { Ui.warn("not inside a project (that is fine for `jreact new`)"); }

        Project forTool = project != null ? project : new Project(Path.of(".").toAbsolutePath(), "shared", "client", "server", 8080, "maven");
        BuildTool tool = BuildTool.of(forTool);
        try {
            BuildTool.Result r = tool.capture(tool.versionArgs(), java.util.Map.of());
            if (r.ok()) Ui.ok(tool.name() + ": " + r.output().strip().lines().filter(l -> !l.isBlank()).findFirst().orElse("ok"));
            else problem(tool.name() + " did not run (" + String.join(" ", tool.base()) + " " + String.join(" ", tool.versionArgs()) + " failed)");
        } catch (java.io.IOException e) {
            problem(tool.name() + " is not installed or not on the PATH (" + e.getMessage() + ")");
        }

        Path repo = Path.of(System.getProperty("user.home"), ".m2", "repository", "dev", "jreact", "jreact-web", v.jreact());
        if (Files.isDirectory(repo)) Ui.ok("JReact " + v.jreact() + " is in the local Maven repository");
        else problem("JReact " + v.jreact() + " is not in ~/.m2. Until it is on Maven Central, run `mvn install -DskipTests` in the JReact repository"
                + (project != null && project.gradle() ? " and `./build.sh gradle-plugin` (the Gradle plugin)" : ""));

        String asset = Tailwind.assetName();
        Path cached = asset == null ? null : Path.of(System.getProperty("user.home"), ".jreact", asset.replace("tailwindcss-", "tailwindcss-" + v.tailwind() + "-"));
        if (System.getenv("TAILWINDCSS") != null) Ui.ok("Tailwind: TAILWINDCSS=" + System.getenv("TAILWINDCSS"));
        else if (cached != null && Files.isExecutable(cached)) Ui.ok("Tailwind " + v.tailwind() + " is downloaded");
        else if (cached != null) Ui.ok("Tailwind " + v.tailwind() + " will be downloaded on first use (about 75 MB, needs internet)");
        else Ui.warn("no Tailwind binary for this platform: set TAILWINDCSS=/path/to/tailwindcss or write plain CSS in client/src/main/webapp/app.css");

        int port = project != null ? project.port() : 8080;
        try (ServerSocket s = new ServerSocket(port)) {
            Ui.ok("port " + port + " is free");
        } catch (java.io.IOException e) {
            Ui.warn("port " + port + " is in use (jreact dev --port 9090 uses another)");
        }

        if (project != null) {
            for (Path module : List.of(project.sharedDir(), project.clientDir(), project.serverDir())) {
                String buildFile = project.gradle() ? "build.gradle" : "pom.xml";
                if (Files.isRegularFile(module.resolve(buildFile))) Ui.ok(project.root().relativize(module) + "/ found");
                else problem(project.root().relativize(module) + "/" + buildFile + " is missing");
            }
        }
        return bad ? 1 : 0;
    }

    private void problem(String text) { Ui.fail(text); bad = true; }
}

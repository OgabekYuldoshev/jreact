package dev.jreact.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Maven: ./mvnw when the project has one, else mvn from the PATH. */
final class Maven extends BuildTool {

    /** Dev builds: no optimization, no hashing, no gzip. Fast, and the file names do not change between rebuilds. */
    static final List<String> DEV_PROPS = List.of("-Djreact.optimization=SIMPLE", "-Djreact.minify=false", "-Djreact.hash=false", "-Djreact.compress=false");

    Maven(Project project) { super(project); }

    @Override String name() { return "Maven"; }

    @Override
    List<String> base() { return List.of(executable(project, "mvnw", "mvn"), "-B"); }

    @Override
    List<String> buildAll() {
        // "." is the parent pom: the server module resolves shared and client from the local repository and needs it there too
        List<String> args = new ArrayList<>(List.of("-q", "-pl", ".," + project.shared() + "," + project.client(), "install", "-DskipTests"));
        args.addAll(DEV_PROPS);
        return args;
    }

    @Override
    List<String> buildClient() {
        List<String> args = new ArrayList<>(List.of("-q", "-pl", project.client(), "process-classes"));
        args.addAll(DEV_PROPS);
        return args;
    }

    @Override List<String> compileServer() { return List.of("-q", "-pl", project.server(), "compile"); }

    @Override List<String> runServer() { return List.of("-q", "-pl", project.server(), "spring-boot:run"); }

    @Override
    List<String> packageJar(boolean skipTests, List<String> extra) {
        List<String> args = new ArrayList<>(List.of("package"));
        if (skipTests) args.add("-DskipTests");
        args.addAll(extra);
        return args;
    }

    @Override
    List<String> test(List<String> extra) {
        List<String> args = new ArrayList<>(List.of("test"));
        args.addAll(DEV_PROPS);        // tests do not need the optimized client
        args.addAll(extra);
        return args;
    }

    @Override
    List<Path> buildFiles() {
        return List.of(project.root().resolve("pom.xml"), project.sharedDir().resolve("pom.xml"), project.clientDir().resolve("pom.xml"), project.serverDir().resolve("pom.xml"));
    }

    @Override Path jarDirectory() { return project.serverDir().resolve("target"); }

    @Override List<String> versionArgs() { return List.of("-v"); }
}

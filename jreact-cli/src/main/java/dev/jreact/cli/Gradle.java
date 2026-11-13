package dev.jreact.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gradle: ./gradlew when the project has one, else gradle from the PATH. The generated client/build.gradle reads {@code -Pjreact.dev=true}
 * (SIMPLE optimization, no minify, no hash, no gzip), the counterpart of Maven's {@code DEV_PROPS}.
 */
final class Gradle extends BuildTool {

    static final String DEV = "-Pjreact.dev=true";

    Gradle(Project project) { super(project); }

    @Override String name() { return "Gradle"; }

    @Override
    List<String> base() { return List.of(executable(project, "gradlew", "gradle"), "--console=plain", "-q"); }

    @Override
    List<String> buildAll() { return List.of(":" + project.shared() + ":classes", ":" + project.client() + ":jrxBundle", DEV); }

    @Override
    List<String> buildClient() { return List.of(":" + project.client() + ":jrxBundle", DEV); }

    @Override List<String> compileServer() { return List.of(":" + project.server() + ":classes", DEV); }

    @Override List<String> runServer() { return List.of(":" + project.server() + ":bootRun", DEV); }

    @Override
    List<String> packageJar(boolean skipTests, List<String> extra) {
        List<String> args = new ArrayList<>(List.of("build"));
        if (skipTests) { args.add("-x"); args.add("test"); }
        args.addAll(extra);
        return args;
    }

    @Override
    List<String> test(List<String> extra) {
        List<String> args = new ArrayList<>(List.of("test", DEV));
        args.addAll(extra);
        return args;
    }

    @Override
    List<Path> buildFiles() {
        return List.of(project.root().resolve("settings.gradle"), project.root().resolve("build.gradle"), project.sharedDir().resolve("build.gradle"),
                project.clientDir().resolve("build.gradle"), project.serverDir().resolve("build.gradle"));
    }

    @Override Path jarDirectory() { return project.serverDir().resolve("build/libs"); }

    @Override List<String> versionArgs() { return List.of("--version"); }
}

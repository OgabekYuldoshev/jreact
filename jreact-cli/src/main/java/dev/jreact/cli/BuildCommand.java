package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "build", description = "Optimized build: minified app.js with a hash in its name, Tailwind CSS, one runnable Spring Boot jar.")
final class BuildCommand implements Callable<Integer> {

    @Option(names = "--skip-tests", description = "do not run the tests")
    boolean skipTests;

    @Parameters(arity = "0..*", paramLabel = "<build tool args>", description = "extra arguments for Maven or Gradle, after --")
    List<String> toolArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        Project project = Project.find(Path.of("."));
        Versions versions = Versions.load();

        Ui.step("CSS");
        Tailwind tailwind = Tailwind.ensure(versions);
        if (tailwind != null) {
            String error = tailwind.build(project, true);
            if (error != null) { Ui.fail(error); return 1; }
            Ui.ok("Tailwind");
        }

        Ui.step("build (TeaVM, optimized: takes a minute)");
        BuildTool tool = BuildTool.of(project);
        int code = tool.inherit(tool.packageJar(skipTests, toolArgs), Map.of());
        if (code != 0) { Ui.fail("the build failed"); return code; }

        Path jar = findJar(project);
        if (jar != null) {
            Ui.ok("built " + project.root().relativize(jar) + " (" + Files.size(jar) / 1024 / 1024 + " MB)");
            Ui.info("  run it:  jreact start        " + Ui.dim("or: java -jar " + project.root().relativize(jar)));
        }
        return 0;
    }

    /** The runnable (repackaged) jar of the server module. */
    static Path findJar(Project project) throws IOException {
        Path target = BuildTool.of(project).jarDirectory();
        if (!Files.isDirectory(target)) return null;
        try (Stream<Path> files = Files.list(target)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".jar") && !f.getFileName().toString().endsWith("-sources.jar")
                    && !f.getFileName().toString().endsWith(".original") && !f.getFileName().toString().endsWith("-plain.jar")).findFirst().orElse(null);
        }
    }
}

package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "test", description = "Runs the tests of every module (mvn test).")
final class TestCommand implements Callable<Integer> {

    @Parameters(arity = "0..*", paramLabel = "<maven args>", description = "extra arguments for Maven, after --")
    List<String> mavenArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        Project project = Project.find(Path.of("."));
        List<String> args = new ArrayList<>(List.of("test"));
        args.addAll(Maven.DEV_PROPS);        // tests do not need the optimized client
        args.addAll(mavenArgs);
        return Maven.inherit(project, args, Map.of());
    }
}

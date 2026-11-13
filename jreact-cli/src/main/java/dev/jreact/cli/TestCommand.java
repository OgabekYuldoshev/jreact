package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "test", description = "Runs the tests of every module (mvn test / gradle test).")
final class TestCommand implements Callable<Integer> {

    @Parameters(arity = "0..*", paramLabel = "<build tool args>", description = "extra arguments for Maven or Gradle, after --")
    List<String> toolArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        Project project = Project.find(Path.of("."));
        BuildTool tool = BuildTool.of(project);
        return tool.inherit(tool.test(toolArgs), Map.of());
    }
}

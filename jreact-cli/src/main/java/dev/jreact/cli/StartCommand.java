package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "start", description = "Runs the jar that `jreact build` made.")
final class StartCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "port (default: server.port of the project)")
    Integer port;

    @Override
    public Integer call() throws Exception {
        Project project = Project.find(Path.of("."));
        Path jar = BuildCommand.findJar(project);
        if (jar == null) throw new IllegalStateException("no jar in " + BuildTool.of(project).jarDirectory() + ". Run: jreact build");
        List<String> cmd = new ArrayList<>(List.of(javaExecutable(), "-jar", jar.toString()));
        cmd.add("--server.port=" + (port != null ? port : project.port()));
        Ui.info("http://localhost:" + (port != null ? port : project.port()));
        Process p = new ProcessBuilder(cmd).directory(project.root().toFile()).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
        return p.waitFor();
    }

    static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", BuildTool.windows() ? "java.exe" : "java").toString();
    }
}

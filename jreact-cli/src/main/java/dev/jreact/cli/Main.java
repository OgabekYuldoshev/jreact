package dev.jreact.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** {@code jreact}: create, run and build a JReact + Spring Boot project. */
@Command(name = "jreact", mixinStandardHelpOptions = true, versionProvider = Main.Version.class,
        description = "JReact: a UI in Java (.jrx) and a Spring Boot API in one project.",
        subcommands = {NewCommand.class, DevCommand.class, BuildCommand.class, StartCommand.class, TestCommand.class, DoctorCommand.class,
                DockerCommand.class, CommandLine.HelpCommand.class})
public final class Main implements Runnable {

    static final class Version implements CommandLine.IVersionProvider {
        @Override public String[] getVersion() { return new String[]{"jreact " + dev.jreact.cli.Versions.load().jreact()}; }
    }

    @Override
    public void run() { new CommandLine(this).usage(System.out); }

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new Main());
        cli.setExecutionExceptionHandler((e, c, parse) -> {
            Ui.fail(e.getMessage() == null ? e.toString() : e.getMessage());
            return 1;
        });
        System.exit(cli.execute(args));
    }
}

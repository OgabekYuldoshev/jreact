package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "new", description = "Creates a project: shared/ (common types), client/ (the UI), server/ (Spring Boot).")
final class NewCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<name>", description = "project name: lowercase letters, digits and dashes (shop, my-app)")
    String name;

    @Option(names = "--group", defaultValue = "com.example", description = "Maven groupId (default: ${DEFAULT-VALUE})")
    String group;

    @Option(names = "--package", description = "Java package (default: <group>.<name without dashes>)")
    String pkg;

    @Option(names = "--build", defaultValue = "maven", description = "build tool: maven or gradle (default: ${DEFAULT-VALUE})")
    String build;

    @Option(names = "--dir", description = "where to create it (default: ./<name>)")
    Path dir;

    @Override
    public Integer call() throws Exception {
        if (!Templates.NAME.matcher(name).matches()) throw new IllegalArgumentException("bad name \"" + name + "\": use lowercase letters, digits and dashes, starting with a letter");
        if (!Templates.PACKAGE.matcher(group).matches()) throw new IllegalArgumentException("bad --group \"" + group + "\" (like com.example)");
        String javaPackage = pkg != null ? pkg : group + "." + name.replace("-", "");
        if (!Templates.PACKAGE.matcher(javaPackage).matches()) throw new IllegalArgumentException("bad --package \"" + javaPackage + "\"");
        if (!build.equals("maven") && !build.equals("gradle")) throw new IllegalArgumentException("--build must be maven or gradle, not \"" + build + "\"");
        Path target = dir != null ? dir : Path.of(name);
        var written = Templates.generate(target, new Templates.Params(name, group, javaPackage, build, Versions.load()));
        Ui.ok("created " + target + " (" + written.size() + " files)");
        Ui.info("");
        Ui.info("  cd " + target);
        Ui.info("  jreact dev        " + Ui.dim("# http://localhost:8080"));
        Ui.info("");
        return 0;
    }
}

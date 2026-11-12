package dev.jreact.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "docker", description = "Writes a Dockerfile that runs the built jar (run `jreact build` first; Docker build copies the jar).")
final class DockerCommand implements Callable<Integer> {

    @Option(names = "--force", description = "overwrite an existing Dockerfile")
    boolean force;

    @Override
    public Integer call() throws Exception {
        Project project = Project.find(Path.of("."));
        Path file = project.root().resolve("Dockerfile");
        if (Files.exists(file) && !force) throw new IllegalStateException(file + " exists (use --force to overwrite)");
        Path jar = BuildCommand.findJar(project);
        String jarPath = jar == null ? project.server() + "/target/*.jar" : project.root().relativize(jar).toString().replace('\\', '/');
        Files.writeString(file, dockerfile(jarPath, project.port()), StandardCharsets.UTF_8);
        Files.writeString(project.root().resolve(".dockerignore"), "**/target/classes\n**/target/test-classes\n**/node_modules\n.git\n", StandardCharsets.UTF_8);
        Ui.ok("wrote " + file);
        Ui.info("  jreact build && docker build -t " + project.root().getFileName() + " . && docker run -p " + project.port() + ":" + project.port() + " " + project.root().getFileName());
        return 0;
    }

    static String dockerfile(String jarPath, int port) {
        return """
                # The jar from `jreact build` (UI and API in one). Spring Boot's layered jars would give smaller image updates:
                # https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html
                FROM eclipse-temurin:17-jre
                RUN useradd --system --create-home app
                USER app
                WORKDIR /home/app
                COPY %s app.jar
                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """.formatted(jarPath, port);
    }
}

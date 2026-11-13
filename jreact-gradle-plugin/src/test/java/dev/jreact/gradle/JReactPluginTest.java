package dev.jreact.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds a small project that applies the plugin (needs `mvn install` of jreact-core and jreact-compiler first, for ~/.m2). */
class JReactPluginTest {

    @TempDir Path project;

    @BeforeEach
    void sample() throws IOException {
        String version = System.getProperty("jreact.version");
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'java'; id 'dev.jreact.jrx' }
                repositories { mavenLocal(); mavenCentral() }
                dependencies { implementation 'dev.jreact:jreact-core:%s' }
                java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
                """.formatted(version));
        Path pkg = Files.createDirectories(project.resolve("src/main/jrx/app"));
        Files.writeString(pkg.resolve("Hello.jrx"), """
                package app;

                import jreact.Component;
                import jreact.Element;

                public class Hello extends Component<Hello.Props> {
                    public record Props(String name) {}

                    @Override
                    public Element render(Props props) {
                        return <p className="hello">Hello, {props.name()}</p>;
                    }
                }
                """);
    }

    private GradleRunner gradle(String... args) {
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(args).forwardOutput();
    }

    @Test
    void jrxCompileGeneratesJavaAndCompileJavaUsesIt() throws IOException {
        BuildResult r = gradle("classes", "--stacktrace").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jrxCompile").getOutcome());
        assertTrue(Files.readString(project.resolve("build/generated/sources/jrx/app/Hello.java")).contains("class Hello"));
        assertTrue(Files.exists(project.resolve("build/classes/java/main/app/Hello.class")), "javac compiled the generated source");
        // nothing changed: up to date
        assertEquals(TaskOutcome.UP_TO_DATE, gradle("classes").build().task(":jrxCompile").getOutcome());
    }

    @Test
    void aSyntaxErrorFailsTheBuildWithTheLocation() throws IOException {
        Files.writeString(project.resolve("src/main/jrx/app/Hello.jrx"), Files.readString(project.resolve("src/main/jrx/app/Hello.jrx")).replace("</p>;", "</div>;"));
        BuildResult r = gradle("jrxCompile").buildAndFail();
        assertTrue(r.getOutput().contains("Hello.jrx:"), r.getOutput());
        assertTrue(r.getOutput().contains("error"), r.getOutput());
    }

    @Test
    void jrxBundleHashesAssetsAndRewritesIndex() throws IOException {
        Path web = Files.createDirectories(project.resolve("build/web"));
        Files.writeString(web.resolve("app.js"), "console.log('hi');");
        Files.writeString(web.resolve("index.html"), "<link href=\"app.css\"><script src=\"app.js\"></script>");
        Files.writeString(web.resolve("favicon.txt"), "x");
        Path tw = Files.createDirectories(project.resolve("build/tailwind"));
        Files.writeString(tw.resolve("app.css"), "body{margin:0}");

        BuildResult r = gradle("jar").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jrxBundle").getOutcome());
        Path out = project.resolve("build/resources/main/jreact");
        String index = Files.readString(out.resolve("index.html"));
        assertTrue(index.matches("(?s).*src=\"/assets/app\\.[0-9a-f]{10}\\.js\".*"), index);
        assertTrue(index.matches("(?s).*href=\"/assets/app\\.[0-9a-f]{10}\\.css\".*"), index);
        try (Stream<Path> files = Files.list(out.resolve("assets"))) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().endsWith(".js.gz")), "gzip copy");
        }
        assertTrue(Files.exists(out.resolve("favicon.txt")));
        assertTrue(Files.exists(out.resolve("manifest.json")));
        // the jar carries the built UI
        assertFalse(Files.list(project.resolve("build/libs")).findAny().isEmpty());
    }
}

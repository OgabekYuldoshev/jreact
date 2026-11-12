package jreact.maven;

import jreact.compiler.JrxCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * {@code jrx:compile}: compiles the project's {@code .jrx} files to Java and adds the result to the compile
 * source roots, so {@code javac} (and TeaVM after it) sees them. Component Props are looked up in the .jrx and
 * .java sources of the project and in the sources that jreact-core ships inside its jar (so {@code ErrorBoundary}
 * and other framework components work without extra setup).
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class CompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Folders with .jrx files. */
    @Parameter(property = "jrx.sourceDirectories")
    private List<File> sourceDirectories;

    /** Extra folders that are only scanned for component Props (e.g. Java sources shared from another module). */
    @Parameter(property = "jrx.indexDirectories")
    private List<File> indexDirectories;

    /** Where the generated .java files go. */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/jrx", property = "jrx.outputDirectory")
    private File outputDirectory;

    @Parameter(defaultValue = "false", property = "jrx.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) { getLog().info("jrx:compile skipped"); return; }

        List<Path> dirs = new ArrayList<>();
        List<File> jrxDirs = sourceDirectories == null || sourceDirectories.isEmpty()
                ? List.of(new File(project.getBasedir(), "src/main/jrx")) : sourceDirectories;
        for (File f : jrxDirs) if (f.isDirectory()) dirs.add(f.toPath());
        if (dirs.isEmpty()) { getLog().info("jrx:compile: no .jrx folder, nothing to do"); return; }
        // the project's own Java sources (and any other source root added so far) may define components too
        for (String root : project.getCompileSourceRoots()) if (new File(root).isDirectory()) dirs.add(Path.of(root));
        if (indexDirectories != null) for (File f : indexDirectories) dirs.add(f.toPath());

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        JrxCompiler.Result result;
        try (PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            result = JrxCompiler.compile(outputDirectory.toPath(), dirs, frameworkSources(), err);
        } catch (IOException e) {
            throw new MojoExecutionException("jrxc failed: " + e.getMessage(), e);
        }
        String messages = errors.toString(StandardCharsets.UTF_8).strip();
        if (result.errors() > 0) {
            throw new MojoFailureException("jrxc: " + result.errors() + " error(s)\n" + messages);
        }
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().info("jrxc: " + result.files() + " file(s) -> " + outputDirectory);
    }

    /** Java sources of every jreact-* dependency jar that carries them (jreact-core does). */
    private List<String> frameworkSources() throws MojoExecutionException {
        List<String> sources = new ArrayList<>();
        for (Artifact a : project.getArtifacts()) {
            if (!"dev.jreact".equals(a.getGroupId()) || a.getFile() == null) continue;
            if (a.getFile().isDirectory()) {      // reactor build that has not packaged yet: target/classes holds the copied sources
                try (Stream<Path> walk = Files.walk(a.getFile().toPath())) {
                    for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                        sources.add(Files.readString(p, StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    throw new MojoExecutionException("cannot read " + a.getFile() + ": " + e.getMessage(), e);
                }
                continue;
            }
            if (!a.getFile().isFile()) continue;
            try (JarFile jar = new JarFile(a.getFile())) {
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                    JarEntry entry = e.nextElement();
                    if (!entry.getName().endsWith(".java")) continue;
                    try (InputStream in = jar.getInputStream(entry)) {
                        sources.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("cannot read " + a.getFile() + ": " + e.getMessage(), e);
            }
        }
        return sources;
    }
}

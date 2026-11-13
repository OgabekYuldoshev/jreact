package dev.jreact.gradle;

import jreact.compiler.JrxCompiler;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Classpath;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** {@code jrxCompile}: .jrx to .java. Component Props are looked up in the .jrx and .java sources and in the sources jreact-core ships in its jar. */
@CacheableTask
public abstract class JrxCompileTask extends DefaultTask {

    /** Folders with .jrx files. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getJrxDirs();

    /** The project's Java sources: they may define components too. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getJavaDirs();

    /** The compile classpath: the jreact-* jars carry the sources of framework components. */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void compile() throws IOException {
        List<Path> dirs = new ArrayList<>();
        for (File f : getJrxDirs()) if (f.isDirectory()) dirs.add(f.toPath());
        if (dirs.isEmpty()) { getLogger().info("jrxCompile: no .jrx folder, nothing to do"); return; }
        for (File f : getJavaDirs()) if (f.isDirectory()) dirs.add(f.toPath());

        File out = getOutputDirectory().get().getAsFile();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        JrxCompiler.Result result;
        try (PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            result = JrxCompiler.compile(out.toPath(), dirs, frameworkSources(), err);
        }
        if (result.errors() > 0) {
            throw new GradleException("jrxc: " + result.errors() + " error(s)\n" + errors.toString(StandardCharsets.UTF_8).strip());
        }
        getLogger().lifecycle("jrxc: " + result.files() + " file(s) -> " + out);
    }

    private List<String> frameworkSources() throws IOException {
        List<String> sources = new ArrayList<>();
        for (File f : getClasspath()) {
            if (!f.isFile() || !f.getName().startsWith("jreact-") || !f.getName().endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(f)) {
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                    JarEntry entry = e.nextElement();
                    if (!entry.getName().endsWith(".java")) continue;
                    try (InputStream in = jar.getInputStream(entry)) {
                        sources.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return sources;
    }
}

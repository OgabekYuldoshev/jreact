package dev.jreact.gradle;

import jreact.compiler.Bundler;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/** {@code jrxBundle}: the TeaVM output to what a server serves (hashed assets, rewritten index.html, .gz, manifest.json). */
@CacheableTask
public abstract class JrxBundleTask extends DefaultTask {

    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getWebDirectory();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getCssFile();

    @Input
    public abstract Property<Boolean> getHash();

    @Input
    public abstract Property<Boolean> getCompress();

    @Input
    public abstract Property<String> getPublicPath();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void bundle() throws IOException {
        if (!getWebDirectory().isPresent() || !getWebDirectory().get().getAsFile().isDirectory()) {
            getLogger().lifecycle("jrxBundle: no web directory (is the TeaVM task wired before this one?), nothing to do");
            return;
        }
        Bundler.Result r = Bundler.bundle(getWebDirectory().get().getAsFile().toPath(),
                getCssFile().isPresent() ? getCssFile().get().getAsFile().toPath() : null,
                getOutputDirectory().get().getAsFile().toPath(), getHash().get(), getCompress().get(), getPublicPath().get(),
                message -> getLogger().warn("jrxBundle: " + message));
        getLogger().lifecycle("jrxBundle: " + r.message());
    }
}

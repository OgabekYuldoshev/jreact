package dev.jreact.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code id 'dev.jreact.jrx'}: with the {@code java} plugin it adds
 * <ul>
 *   <li>{@code jrxCompile}: src/main/jrx to build/generated/sources/jrx, which is added to the main Java sources ({@code compileJava} depends on it)</li>
 *   <li>{@code jrxBundle}: the TeaVM output to hashed assets under build/resources/main/jreact, so the jar of the module carries the built UI.
 *       Make it run after the task that runs TeaVM: {@code jrxBundle { dependsOn 'generateJavaScript' }}</li>
 * </ul>
 */
public class JReactPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        JReactExtension ext = project.getExtensions().create("jreact", JReactExtension.class);
        ext.getJrxDirs().from(project.file("src/main/jrx"));
        ext.getWebDirectory().convention(project.getLayout().getBuildDirectory().dir("web"));
        ext.getCssFile().convention(project.getLayout().getBuildDirectory().file("tailwind/app.css"));
        ext.getHash().convention(true);
        ext.getCompress().convention(true);
        ext.getPublicPath().convention("/");

        project.getPlugins().withType(JavaPlugin.class, java -> {
            SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            // the project's own Java sources, taken before the generated folder is added to them (a task must not read its own output)
            java.io.File[] ownSources = main.getJava().getSrcDirs().toArray(new java.io.File[0]);

            TaskProvider<JrxCompileTask> compile = project.getTasks().register("jrxCompile", JrxCompileTask.class, t -> {
                t.setDescription("Compiles .jrx files to Java.");
                t.setGroup("jreact");
                t.getJrxDirs().from(ext.getJrxDirs());
                t.getJavaDirs().from((Object[]) ownSources);
                t.getClasspath().from(main.getCompileClasspath());
                t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/sources/jrx"));
            });
            main.getJava().srcDir(compile.flatMap(JrxCompileTask::getOutputDirectory));
            project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME, t -> t.dependsOn(compile));

            TaskProvider<JrxBundleTask> bundle = project.getTasks().register("jrxBundle", JrxBundleTask.class, t -> {
                t.setDescription("Hashed, compressed assets and index.html for the server (after the TeaVM task).");
                t.setGroup("jreact");
                t.getWebDirectory().set(ext.getWebDirectory());
                t.getCssFile().set(ext.getCssFile().map(f -> f.getAsFile().isFile() ? f : null));
                t.getHash().set(ext.getHash());
                t.getCompress().set(ext.getCompress());
                t.getPublicPath().set(ext.getPublicPath());
                t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("resources/main/jreact"));
            });
            project.getTasks().named(JavaPlugin.JAR_TASK_NAME, t -> t.dependsOn(bundle));
        });
    }
}

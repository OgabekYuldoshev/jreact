package jreact.maven;

import jreact.compiler.Bundler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

/**
 * {@code jrx:page}: for a plain browser build (TeaVM output, no server): writes {@code index.html} next to {@code app.js} in the web folder,
 * with relative {@code app.js}/{@code app.css} references, from the same settings as the server build ({@code title}, {@code bodyClass}, {@code head}...).
 * A {@code src/main/webapp/index.html} of your own is kept as it is (nothing is written), and, since this runs after the web folder is filled,
 * {@code src/main/webapp/head.html} and the favicon are picked up.
 */
@Mojo(name = "page", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public class PageMojo extends AbstractPageMojo {

    @Parameter(defaultValue = "${project.build.directory}/web", property = "jreact.webDirectory")
    private File webDirectory;

    /** Link app.css. Default: yes when the project has src/main/styles/app.css (Tailwind) or src/main/webapp/app.css. */
    @Parameter(property = "jreact.css")
    private Boolean css;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        boolean link = css != null ? css
                : new File(project.getBasedir(), "src/main/styles/app.css").isFile() || new File(project.getBasedir(), "src/main/webapp/app.css").isFile();
        if (new File(project.getBasedir(), "src/main/webapp/index.html").isFile()) {
            getLog().info("jrx:page: src/main/webapp/index.html is used as it is");
            return;
        }
        try {
            Bundler.writePage(webDirectory.toPath(), link, page());
            getLog().info("jrx:page: wrote " + new File(webDirectory, "index.html"));
        } catch (IOException e) {
            throw new MojoExecutionException("jrx:page failed: " + e.getMessage(), e);
        }
    }
}

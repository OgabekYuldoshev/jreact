package jreact.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import jreact.compiler.Bundler;

import java.io.File;
import java.io.IOException;

/**
 * {@code jrx:bundle}: turns the TeaVM output into what a server serves. {@code app.js} and {@code app.css} get a content hash in
 * their name ({@code assets/app.3f9a1c2b.js}, cacheable forever), {@code index.html} is generated (or, if src/main/webapp has one, rewritten) to point at them, a gzip copy is
 * written next to each, and {@code manifest.json} lists the result. Every other file of the web folder is copied as is.
 * The output goes to {@code target/classes/jreact} so that it lands in the module's jar and a Spring Boot server finds it on its classpath.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class BundleMojo extends AbstractMojo {

    /** The TeaVM result: app.js, index.html and the other files of src/main/webapp. */
    @Parameter(defaultValue = "${project.build.directory}/web", property = "jreact.webDirectory")
    private File webDirectory;

    /** Compiled CSS (what the jreact CLI's Tailwind step writes). When missing, {@code app.css} of the web folder is used if there is one. */
    @Parameter(defaultValue = "${project.build.directory}/tailwind/app.css", property = "jreact.cssFile")
    private File cssFile;

    @Parameter(defaultValue = "${project.build.outputDirectory}/jreact", property = "jreact.outputDirectory")
    private File outputDirectory;

    /** Put a content hash in the file names. Off in dev, so the names stay the same between rebuilds. */
    @Parameter(defaultValue = "true", property = "jreact.hash")
    private boolean hash;

    /** Write .gz copies of js, css and html. */
    @Parameter(defaultValue = "true", property = "jreact.compress")
    private boolean compress;

    /** URL prefix of the assets in index.html. */
    @Parameter(defaultValue = "/", property = "jreact.publicPath")
    private String publicPath;

    /** The generated index.html (used only when src/main/webapp has no index.html of its own). */
    @Parameter(defaultValue = "App", property = "jreact.title")
    private String title;

    @Parameter(defaultValue = "en", property = "jreact.lang")
    private String lang;

    /** CSS classes of the html, body and root elements (Tailwind utilities, for example). */
    @Parameter(property = "jreact.htmlClass")
    private String htmlClass;

    @Parameter(property = "jreact.bodyClass")
    private String bodyClass;

    @Parameter(property = "jreact.rootClass")
    private String rootClass;

    /** Raw HTML for the end of head (analytics, Open Graph tags, fonts). Longer content: put it in src/main/webapp/head.html. */
    @Parameter(property = "jreact.head")
    private String head;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Bundler.Result r = Bundler.bundle(webDirectory.toPath(), cssFile == null ? null : cssFile.toPath(), outputDirectory.toPath(),
                    hash, compress, publicPath, new Bundler.Page(title, lang, htmlClass, bodyClass, rootClass, head), message -> getLog().warn("jrx:bundle: " + message));
            getLog().info("jrx:bundle: " + r.message());
        } catch (IOException e) {
            throw new MojoExecutionException("jrx:bundle failed: " + e.getMessage(), e);
        }
    }
}

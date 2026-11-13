package jreact.maven;

import jreact.compiler.Bundler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/** What the generated index.html says (used by {@code jrx:bundle} and {@code jrx:page}). */
abstract class AbstractPageMojo extends AbstractMojo {

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

    final Bundler.Page page() {
        return new Bundler.Page(title, lang, htmlClass, bodyClass, rootClass, head);
    }
}

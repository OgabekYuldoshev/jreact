package dev.jreact.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * {@code jreact { ... }} in build.gradle.
 * <pre>
 * jreact {
 *     jrxDirs.from('src/main/jrx')          // default
 *     webDirectory = layout.buildDirectory.dir('web')   // the TeaVM output (app.js) and src/main/webapp files
 *     cssFile = layout.buildDirectory.file('tailwind/app.css')
 *     hash = true                           // content hash in asset names
 *     compress = true                       // .gz next to js, css, html
 *     publicPath = '/'
 * }
 * </pre>
 */
public abstract class JReactExtension {

    /** Folders with .jrx files. */
    public abstract ConfigurableFileCollection getJrxDirs();

    /** Where TeaVM wrote app.js (with index.html and the other static files). */
    public abstract DirectoryProperty getWebDirectory();

    /** Compiled CSS (Tailwind). Optional; {@code app.css} of the web directory is used when this file does not exist. */
    public abstract RegularFileProperty getCssFile();

    public abstract Property<Boolean> getHash();

    public abstract Property<Boolean> getCompress();

    /** URL prefix of the assets in index.html. */
    public abstract Property<String> getPublicPath();
}

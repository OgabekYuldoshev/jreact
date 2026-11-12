package dev.jreact.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code jreact.*} in application.properties.
 * <pre>
 * jreact.enabled=true                      # serve the app at all
 * jreact.location=classpath:/jreact/       # where the client module put its files (index.html, assets/)
 * jreact.reserved-prefixes=/api,/_rpc      # unknown URLs under these answer 404, never index.html
 * jreact.dev-dir=../client/target/classes/jreact   # dev mode: serve from this folder, reload the browser when it changes
 * </pre>
 */
@ConfigurationProperties("jreact")
public class JReactProperties {

    private boolean enabled = true;
    private String location = "classpath:/jreact/";
    private List<String> reservedPrefixes = List.of("/api", "/_rpc", "/actuator", "/error", "/__jreact");
    private String devDir;
    private String devErrorFile;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<String> getReservedPrefixes() { return reservedPrefixes; }
    public void setReservedPrefixes(List<String> reservedPrefixes) { this.reservedPrefixes = reservedPrefixes; }

    /** Dev mode on when set: files come from this folder (no restart needed) and pages get the reload script. */
    public String getDevDir() { return devDir; }
    public void setDevDir(String devDir) { this.devDir = devDir; }

    /** Dev mode: a file the jreact CLI writes when the client build fails; its text is shown over the page. */
    public String getDevErrorFile() { return devErrorFile; }
    public void setDevErrorFile(String devErrorFile) { this.devErrorFile = devErrorFile; }

    public boolean isDev() { return devDir != null && !devDir.isBlank(); }
}

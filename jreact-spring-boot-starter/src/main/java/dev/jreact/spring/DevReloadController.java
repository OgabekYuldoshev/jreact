package dev.jreact.spring;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev mode only (jreact.dev-dir is set). The page polls {@code /__jreact/version}; when the client build changes it, the page reloads.
 * When a build fails, the CLI writes the compiler output to the error file and the page shows it in an overlay.
 */
@RestController
class DevReloadController {

    private final Path manifest;
    private final Path errorFile;

    DevReloadController(JReactProperties props) {
        this.manifest = Path.of(props.getDevDir()).resolve("manifest.json");
        this.errorFile = props.getDevErrorFile() == null || props.getDevErrorFile().isBlank() ? null : Path.of(props.getDevErrorFile());
    }

    @GetMapping(value = "/__jreact/version", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(stamp(manifest) + "|" + (errorFile == null ? "0" : stamp(errorFile)));
    }

    @GetMapping(value = "/__jreact/error", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> error() throws IOException {
        String text = errorFile != null && Files.isRegularFile(errorFile) ? Files.readString(errorFile, StandardCharsets.UTF_8) : "";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(text);
    }

    @GetMapping(value = "/__jreact/reload.js", produces = "application/javascript")
    ResponseEntity<String> script() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RELOAD_JS);
    }

    private static String stamp(Path p) {
        try { return Files.isRegularFile(p) ? Files.getLastModifiedTime(p).toMillis() + "-" + Files.size(p) : "0"; }
        catch (IOException e) { return "0"; }
    }

    private static final String RELOAD_JS = """
            (function () {
              var base = new URL(document.currentScript.src).pathname.replace(/\\/__jreact\\/reload\\.js$/, '');     // the context path, if any
              var current = null, box = null;
              function overlay(text) {
                if (!text) { if (box) { box.remove(); box = null; } return; }
                if (!box) {
                  box = document.createElement('pre');
                  box.setAttribute('style', 'position:fixed;inset:0;z-index:2147483647;margin:0;padding:24px;overflow:auto;background:rgba(20,10,10,.96);color:#ffb4b4;font:13px/1.5 ui-monospace,Menlo,monospace;white-space:pre-wrap');
                  document.body.appendChild(box);
                }
                box.textContent = 'JReact build failed\\n\\n' + text;
              }
              function tick() {
                fetch(base + '/__jreact/version', {cache: 'no-store'}).then(function (r) { return r.text(); }).then(function (v) {
                  if (current === null) current = v;
                  else if (v !== current) {
                    current = v;
                    fetch(base + '/__jreact/error', {cache: 'no-store'}).then(function (r) { return r.text(); }).then(function (t) {
                      if (t.trim()) overlay(t); else location.reload();
                    });
                  }
                }).catch(function () {}).then(function () { setTimeout(tick, 400); });
              }
              tick();
            })();
            """;
}

package dev.jreact.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * Tailwind's standalone executable (no Node needed): downloaded once into ~/.jreact and run before each client build.
 * {@code TAILWINDCSS=/path/to/binary} uses your own.
 */
final class Tailwind {
    private final Path binary;

    private Tailwind(Path binary) { this.binary = binary; }

    /** The binary, downloading it on first use. Returns null (after saying why) when it cannot be had. */
    static Tailwind ensure(Versions v) {
        String own = System.getenv("TAILWINDCSS");
        if (own != null && !own.isBlank()) {
            Path p = Path.of(own);
            if (Files.isExecutable(p)) return new Tailwind(p);
            Ui.warn("TAILWINDCSS=" + own + " is not an executable file, ignoring it");
        }
        String asset = assetName();
        if (asset == null) { Ui.warn("no Tailwind binary for " + System.getProperty("os.name") + "/" + System.getProperty("os.arch") + ", CSS is skipped"); return null; }
        Path cached = Path.of(System.getProperty("user.home"), ".jreact", asset.replace("tailwindcss-", "tailwindcss-" + v.tailwind() + "-"));
        if (Files.isExecutable(cached)) return new Tailwind(cached);
        try {
            Ui.step("downloading Tailwind CSS " + v.tailwind() + " (once)");
            download("https://github.com/tailwindlabs/tailwindcss/releases/download/" + v.tailwind() + "/" + asset, cached);
            return new Tailwind(cached);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Ui.warn("could not download Tailwind (" + e.getMessage() + "). CSS is skipped; set TAILWINDCSS=/path/to/tailwindcss to use your own");
            return null;
        }
    }

    static String assetName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.contains("amd64") || arch.contains("x86_64");
        if (os.contains("mac")) return arm ? "tailwindcss-macos-arm64" : x64 ? "tailwindcss-macos-x64" : null;
        if (os.contains("linux")) return arm ? "tailwindcss-linux-arm64" : x64 ? "tailwindcss-linux-x64" : null;
        if (os.contains("win")) return x64 ? "tailwindcss-windows-x64.exe" : null;
        return null;
    }

    private static void download(String url, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<java.io.InputStream> r = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " from " + url);
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (java.io.InputStream in = r.body()) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
        finally { java.lang.ref.Reference.reachabilityFence(client); }   // an unreferenced client is closed by the JDK, which cuts the download off ("closed")
        if (!tmp.toFile().setExecutable(true)) throw new IOException("cannot make " + tmp + " executable");
        signForMac(tmp);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * The downloaded macOS binary is only linker-signed, and recent macOS versions kill it at launch (exit 137, no message).
     * Signing it ad hoc on this machine fixes that.
     */
    private static void signForMac(Path file) throws IOException, InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return;
        Process p = new ProcessBuilder("codesign", "--force", "--sign", "-", file.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) Ui.warn("codesign failed, Tailwind may not start: " + out.strip());
    }

    /** Compiles the project's app.css into client/target/tailwind/app.css. Returns the error text, or null when fine. */
    String build(Project project, boolean minify) throws IOException, InterruptedException {
        if (!Files.isRegularFile(project.cssInput())) return null;       // no Tailwind input: the project brings its own CSS
        Files.createDirectories(project.cssOutput().getParent());
        List<String> cmd = new java.util.ArrayList<>(List.of(binary.toString(), "-i", project.cssInput().toString(), "-o", project.cssOutput().toString()));
        if (minify) cmd.add("--minify");
        Process p = new ProcessBuilder(cmd).directory(project.clientDir().toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code == 0) return null;
        return "Tailwind failed (exit " + code + "):\n" + (out.isBlank() && code == 137 ? "the OS killed the binary " + binary + ". Delete it (rm " + binary + ") and run again, or set TAILWINDCSS=/path/to/tailwindcss" : out);
    }
}

package jreact.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free test of the Bundler's generated index.html (run: ./build.sh test). */
public final class BundlerTest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("bundler");
        Path web = Files.createDirectories(tmp.resolve("web"));
        Files.writeString(web.resolve("app.js"), "function main(){}");
        Files.writeString(web.resolve("app.css"), "body{}");
        Files.writeString(web.resolve("favicon.svg"), "<svg/>");
        Files.writeString(web.resolve("head.html"), "<meta name=\"description\" content=\"from the fragment\">\n<script defer src=\"/stats.js\"></script>");
        Path out = tmp.resolve("out");
        List<String> warnings = new ArrayList<>();

        // no index.html: generated from the Page
        Bundler.Page page = new Bundler.Page("Ali & \"Vali\" <Books>", "uz", "h-full", "h-full bg-slate-50", "min-h-full", "<meta name=\"theme-color\" content=\"#fff\">");
        Bundler.Result r = Bundler.bundle(web, null, out, true, false, "/", page, warnings::add);
        String html = Files.readString(out.resolve("index.html"));
        check("generated: ran, no warnings", r.ran() && warnings.isEmpty(), warnings);
        check("doctype, lang and html class", html.startsWith("<!doctype html>\n<html lang=\"uz\" class=\"h-full\">"), html);
        check("title is escaped", html.contains("<title>Ali &amp; \"Vali\" &lt;Books&gt;</title>"), html);
        check("hashed stylesheet and script", html.contains("href=\"/" + r.cssName() + "\"") && html.contains("src=\"/" + r.jsName() + "\"") && r.jsName().matches("assets/app\\.[0-9a-f]{10}\\.js"), html);
        check("favicon is linked", html.contains("<link rel=\"icon\" href=\"/favicon.svg\">"), html);
        check("head from the config and from head.html", html.contains("theme-color") && html.contains("from the fragment") && html.contains("/stats.js"), html);
        check("head content is before </head>", html.indexOf("theme-color") < html.indexOf("</head>") && html.indexOf("/stats.js") < html.indexOf("</head>"), html);
        check("body and root classes, root id, main()", html.contains("<body class=\"h-full bg-slate-50\">") && html.contains("<div id=\"root\" class=\"min-h-full\"></div>") && html.contains("<script>main();</script>"), html);
        check("head.html and index.html are not copied as files", !Files.exists(out.resolve("head.html")) && Files.exists(out.resolve("favicon.svg")), "");

        // defaults and public path
        Bundler.bundle(web, null, out, false, true, "/app", Bundler.Page.DEFAULT, warnings::add);
        String plain = Files.readString(out.resolve("index.html"));
        check("defaults: lang en, title App, no class attributes", plain.startsWith("<!doctype html>\n<html lang=\"en\">") && plain.contains("<title>App</title>") && plain.contains("<body>") && plain.contains("<div id=\"root\"></div>"), plain);
        check("public path prefix, no hash", plain.contains("src=\"/app/assets/app.js\"") && plain.contains("href=\"/app/assets/app.css\""), plain);
        check("gzip copy of index.html", Files.exists(out.resolve("index.html.gz")), "");

        // an index.html of your own wins and only has its asset paths rewritten
        Files.writeString(web.resolve("index.html"), "<title>mine</title><link href=\"app.css\"><script src=\"app.js\"></script><div id=\"root\"></div>");
        Bundler.Result own = Bundler.bundle(web, null, out, true, false, "/", page, warnings::add);
        String mine = Files.readString(out.resolve("index.html"));
        check("own index.html is kept", mine.startsWith("<title>mine</title>") && !mine.contains("Vali") && mine.contains(own.jsName()) && mine.contains(own.cssName()), mine);

        System.out.println(passed + " passed" + (failures.isEmpty() ? "" : ", " + failures.size() + " FAILED"));
        for (String f : failures) System.out.println("FAIL " + f);
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void check(String name, boolean ok, Object detail) {
        if (ok) { passed++; System.out.println("ok   " + name); }
        else failures.add(name + ": " + detail);
    }
}

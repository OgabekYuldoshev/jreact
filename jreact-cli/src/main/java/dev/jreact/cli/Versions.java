package dev.jreact.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The versions a new project is generated with (filtered into version.properties at build time). */
record Versions(String jreact, String springBoot, String teavm, String tailwind) {

    static Versions load() {
        Properties p = new Properties();
        try (InputStream in = Versions.class.getResourceAsStream("/version.properties")) {
            if (in == null) throw new IllegalStateException("version.properties is missing from the CLI jar");
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new Versions(p.getProperty("jreact"), p.getProperty("springBoot"), p.getProperty("teavm"), p.getProperty("tailwind"));
    }
}

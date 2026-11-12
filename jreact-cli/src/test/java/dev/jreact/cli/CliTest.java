package dev.jreact.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {

    static final Versions V = new Versions("1.2.3", "3.5.0", "0.16.0", "v4.1.11");

    @Test
    void indexListsEveryTemplateFile() throws IOException {
        Path root = Path.of("src/main/resources/templates/spring");
        Set<String> onDisk;
        try (Stream<Path> walk = Files.walk(root)) {
            onDisk = walk.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/')).filter(n -> !n.equals("index.txt")).collect(Collectors.toSet());
        }
        assertEquals(onDisk, new HashSet<>(Templates.files()), "templates/spring/index.txt must list exactly the template files");
    }

    @Test
    void generatesAProjectWithEveryPlaceholderFilled(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("shop");
        var written = Templates.generate(target, new Templates.Params("shop", "com.acme", "com.acme.shop", V));
        assertEquals(Templates.files().size(), written.size());
        assertTrue(Files.isRegularFile(target.resolve("jreact.properties")));
        assertTrue(Files.isRegularFile(target.resolve(".gitignore")));
        assertTrue(Files.isRegularFile(target.resolve("client/src/main/jrx/com/acme/shop/client/WebMain.jrx")));
        assertTrue(Files.isRegularFile(target.resolve("server/src/main/java/com/acme/shop/server/Application.java")));
        assertTrue(Files.isRegularFile(target.resolve("shared/src/main/java/com/acme/shop/shared/Todo.java")));
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path f : walk.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(f);
                assertFalse(text.contains("{{"), f + " still has a placeholder");
                assertFalse(text.contains("__pkg__"), f + " still has __pkg__");
            }
        }
        String rootPom = Files.readString(target.resolve("pom.xml"));
        assertTrue(rootPom.contains("<groupId>com.acme</groupId>") && rootPom.contains("<jreact.version>1.2.3</jreact.version>") && rootPom.contains("<spring-boot.version>3.5.0</spring-boot.version>"));
        assertTrue(Files.readString(target.resolve("client/pom.xml")).contains("<mainClass>com.acme.shop.client.WebMain</mainClass>"));
        assertTrue(Files.readString(target.resolve("client/src/main/jrx/com/acme/shop/client/WebMain.jrx")).startsWith("package com.acme.shop.client;"));
        assertTrue(Project.load(target).port() == 8080);
    }

    @Test
    void refusesANonEmptyFolder(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("keep.txt"), "mine");
        assertThrows(IOException.class, () -> Templates.generate(dir, new Templates.Params("shop", "com.acme", "com.acme.shop", V)));
        assertEquals("mine", Files.readString(dir.resolve("keep.txt")));
    }

    @Test
    void validatesNames() {
        for (String ok : List.of("shop", "my-app", "a1", "x-y-z")) assertTrue(Templates.NAME.matcher(ok).matches(), ok);
        for (String bad : List.of("Shop", "1app", "my_app", "-a", "a-", "a--b", "my app", "", "../x")) assertFalse(Templates.NAME.matcher(bad).matches(), bad);
        assertTrue(Templates.PACKAGE.matcher("com.example.shop").matches());
        assertFalse(Templates.PACKAGE.matcher("com.Example").matches());
        assertFalse(Templates.PACKAGE.matcher("com..x").matches());
    }

    @Test
    void findsTheProjectFromASubfolder(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("jreact.properties"), "port=9090\nclient=ui\n");
        Path deep = Files.createDirectories(dir.resolve("ui/src/main"));
        Project p = Project.find(deep);
        assertEquals(dir.toRealPath(), p.root().toRealPath());
        assertEquals(9090, p.port());
        assertEquals("ui", p.client());
        assertEquals("shared", p.shared());
    }

    @Test
    void saysSoOutsideAProject(@TempDir Path dir) {
        // (a jreact.properties above the temp folder would make this fail: there is none in a normal temp dir)
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> Project.find(dir));
        assertTrue(e.getMessage().contains("jreact new"));
    }

    @Test
    void dockerfileCopiesTheJarAndOpensThePort() {
        String d = DockerCommand.dockerfile("server/target/app.jar", 8081);
        assertTrue(d.contains("COPY server/target/app.jar app.jar") && d.contains("EXPOSE 8081") && d.contains("USER app"));
    }

    @Test
    void theDevScanSeesChangesButNotEditorTempFiles(@TempDir Path dir) throws Exception {
        Path src = Files.createDirectories(dir.resolve("client/src/main"));
        Files.writeString(src.resolve("A.jrx"), "a");
        var before = DevCommand.scan(dir.resolve("client"));
        Files.writeString(src.resolve("A.jrx.swp"), "x");
        Files.writeString(src.resolve("B.jrx~"), "x");
        assertEquals(before, DevCommand.scan(dir.resolve("client")));
        Files.writeString(src.resolve("A.jrx"), "ab");
        assertFalse(before.equals(DevCommand.scan(dir.resolve("client"))));
    }

    @Test
    void tailKeepsTheEndOfALog() {
        assertEquals("c\nd", Maven.tail("a\nb\nc\nd\n", 2));
    }
}

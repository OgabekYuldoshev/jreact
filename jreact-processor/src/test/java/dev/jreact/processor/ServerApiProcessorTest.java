package dev.jreact.processor;

import dev.jreact.api.Rpc;
import dev.jreact.api.RpcDispatcher;
import dev.jreact.api.RpcException;
import jreact.Json;
import jreact.router.Promise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerApiProcessorTest {

    // ---------------------------------------------------------------- compile helper

    record Compiled(List<String> errors, Path out, ClassLoader loader) {}

    static Compiled compile(Path dir, Map<String, String> sources) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, code) -> units.add(new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return code; }
        }));
        Path out = Files.createDirectories(dir.resolve("out"));
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, null, null)) {
            fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(out.toFile()));
            // -processor is explicit because JDK 23+ no longer discovers processors on the classpath by itself
            boolean ok = javac.getTask(null, fm, diagnostics, List.of("-classpath", System.getProperty("java.class.path"), "--release", "17", "-processor", "dev.jreact.processor.ServerApiProcessor"), null, units).call();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR) errors.add(d.getMessage(null));
            if (ok != errors.isEmpty()) throw new AssertionError("javac result and diagnostics disagree: " + errors);
            return new Compiled(errors, out, new URLClassLoader(new URL[]{out.toUri().toURL()}, ServerApiProcessorTest.class.getClassLoader()));
        }
    }

    static final String MODEL = """
            package demo;
            public enum Priority { LOW, HIGH }
            """;
    static final String TODO = """
            package demo;
            public record Todo(long id, String title, boolean done, Priority priority, java.util.List<String> tags) {}
            """;
    static final String API = """
            package demo;
            import dev.jreact.api.ServerApi;
            import java.util.List;
            import java.util.Map;
            @ServerApi
            public interface TodoApi {
                List<Todo> list();
                Todo add(String title, Priority priority);
                void remove(long id);
                Map<String, Integer> counts();
                int sum(List<Integer> numbers);
                Todo find(long id);
            }
            """;
    static final String IMPL = """
            package demo;
            import java.util.*;
            public class Impl implements TodoApi {
                public List<String> log = new ArrayList<>();
                public List<Todo> list() { return List.of(new Todo(1, "a", false, Priority.LOW, List.of("x", "y"))); }
                public Todo add(String title, Priority p) { return new Todo(7, title, false, p, List.of()); }
                public void remove(long id) { log.add("removed " + id); }
                public Map<String, Integer> counts() { Map<String, Integer> m = new LinkedHashMap<>(); m.put("open", 2); return m; }
                public int sum(List<Integer> numbers) { int s = 0; for (int n : numbers) s += n; return s; }
                public Todo find(long id) { if (id == 404) throw new dev.jreact.api.RpcException(404, "no todo 404"); return null; }
            }
            """;

    static Compiled sample;
    static Object impl;
    static RpcDispatcher dispatcher;

    @BeforeAll
    static void build(@TempDir Path dir) throws Exception {
        sample = compile(dir, Map.of("demo.Priority", MODEL, "demo.Todo", TODO, "demo.TodoApi", API, "demo.Impl", IMPL));
        assertEquals(List.of(), sample.errors());
        impl = sample.loader().loadClass("demo.Impl").getDeclaredConstructor().newInstance();
        dispatcher = (RpcDispatcher) sample.loader().loadClass("demo.TodoApiDispatcher").getDeclaredConstructor().newInstance();
    }

    @SuppressWarnings("unchecked")
    static Object call(String method, Object... args) { return dispatcher.invoke(impl, method, new ArrayList<>(List.of(args))); }

    static String json(Object v) { return Json.stringify(v); }

    // ---------------------------------------------------------------- generated dispatcher

    @Test
    void generatesTheThreeClassesAndTheServiceList() throws Exception {
        for (String c : List.of("demo.TodoApiClient", "demo.TodoApiDispatcher", "demo.TodoApiWire")) assertNotNull(sample.loader().loadClass(c), c);
        assertEquals("demo.TodoApiDispatcher\n", Files.readString(sample.out().resolve("META-INF/jreact/rpc-dispatchers")));
        assertEquals("TodoApi", dispatcher.name());
        assertEquals("demo.TodoApi", dispatcher.api().getName());
    }

    @Test
    void recordsEnumsListsAndMapsConvertBothWays() throws Exception {
        assertEquals("{\"id\":7,\"title\":\"milk\",\"done\":false,\"priority\":\"HIGH\",\"tags\":[]}", json(call("add", "milk", "HIGH")));
        assertEquals("[{\"id\":1,\"title\":\"a\",\"done\":false,\"priority\":\"LOW\",\"tags\":[\"x\",\"y\"]}]", json(call("list")));
        assertEquals("{\"open\":2}", json(call("counts")));
        assertEquals(6, ((Number) call("sum", List.of(1L, 2L, 3L))).intValue());
        assertEquals(null, call("remove", 5L));
        assertTrue(((java.util.List<?>) sample.loader().loadClass("demo.Impl").getField("log").get(impl)).contains("removed 5"));
        assertEquals(null, call("find", 1L));
    }

    @Test
    void aWrongShapeIsA400ThatNamesTheParameter() {
        RpcException e = assertThrows(RpcException.class, () -> call("add", 5L, "HIGH"));
        assertEquals(400, e.status());
        assertEquals("title: expected a string, got a number", e.getMessage());
        assertEquals("priority: \"URGENT\" is not one of the allowed values", assertThrows(RpcException.class, () -> call("add", "x", "URGENT")).getMessage());
        assertEquals("id: expected an integer, got a string", assertThrows(RpcException.class, () -> call("remove", "7")).getMessage());
        assertEquals("numbers[1]: expected an integer, got a string", assertThrows(RpcException.class, () -> call("sum", List.of(1L, "x"))).getMessage());
        assertEquals("numbers: expected an array, got an object", assertThrows(RpcException.class, () -> call("sum", Map.of())).getMessage());
    }

    @Test
    void theNumberOfArgumentsAndTheMethodNameAreChecked() {
        assertEquals("expected 2 argument(s), got 1", assertThrows(RpcException.class, () -> call("add", "x")).getMessage());
        assertEquals(404, assertThrows(RpcException.class, () -> call("nothing")).status());
        assertEquals("no todo 404", assertThrows(RpcException.class, () -> call("find", 404L)).getMessage());
    }

    // ---------------------------------------------------------------- generated client against a loopback transport

    @Test
    void theClientSendsJsonAndReturnsTypedValues() throws Exception {
        List<String> wire = new ArrayList<>();
        Rpc.transport((api, method, args) -> {
            String request = Json.stringify(args);
            wire.add(api + "/" + method + " " + request);
            @SuppressWarnings("unchecked") List<Object> parsed = (List<Object>) Json.parse(request);   // what the server would read
            try {
                return Promise.resolved(Json.parse(Json.stringify(dispatcher.invoke(impl, method, parsed))));
            } catch (RuntimeException e) {
                return Promise.rejected(e);
            }
        });
        try {
            Class<?> client = sample.loader().loadClass("demo.TodoApiClient");
            Class<?> priority = sample.loader().loadClass("demo.Priority");
            Object high = priority.getField("HIGH").get(null);

            Object[] holder = new Object[1];
            ((Promise<?>) client.getMethod("add", String.class, priority).invoke(null, "milk", high)).whenDone((v, e) -> holder[0] = e == null ? v : e);
            assertEquals("demo.Todo", holder[0].getClass().getName());
            assertEquals("Todo[id=7, title=milk, done=false, priority=HIGH, tags=[]]", holder[0].toString());
            assertEquals("TodoApi/add [\"milk\",\"HIGH\"]", wire.get(0));

            ((Promise<?>) client.getMethod("sum", List.class).invoke(null, List.of(4, 5))).whenDone((v, e) -> holder[0] = e == null ? v : e);
            assertEquals(9, holder[0]);

            ((Promise<?>) client.getMethod("remove", long.class).invoke(null, 3L)).whenDone((v, e) -> holder[0] = e == null ? "done" : e);
            assertEquals("done", holder[0]);

            ((Promise<?>) client.getMethod("find", long.class).invoke(null, 404L)).whenDone((v, e) -> holder[0] = e);
            assertEquals("no todo 404", ((Throwable) holder[0]).getMessage());
        } finally {
            Rpc.transport(null);
        }
    }

    @Test
    void withoutATransportTheCallFailsWithAHint() throws Exception {
        Rpc.transport(null);
        Object[] holder = new Object[1];
        ((Promise<?>) sample.loader().loadClass("demo.TodoApiClient").getMethod("list").invoke(null)).whenDone((v, e) -> holder[0] = e);
        assertTrue(((Throwable) holder[0]).getMessage().contains("no RPC transport"));
    }

    // ---------------------------------------------------------------- compile errors

    static List<String> errorsOf(Path dir, String api, String... extra) throws IOException {
        Map<String, String> sources = new java.util.HashMap<>();
        sources.put("demo.Api", api);
        for (int i = 0; i < extra.length; i += 2) sources.put(extra[i], extra[i + 1]);
        return compile(dir, sources).errors();
    }

    // javac reports errors through the DiagnosticCollector, so compile() throws only when the two disagree
    static Compiled compileFailing(Path dir, Map<String, String> sources) throws IOException { return compile(dir, sources); }

    @Test
    void anUnsupportedTypeIsACompileErrorThatSaysWhatIsAllowed(@TempDir Path dir) throws Exception {
        List<String> errors = errorsOf(dir, "package demo; @dev.jreact.api.ServerApi public interface Api { void when(java.util.Date d); }");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("java.util.Date") && errors.get(0).contains("Allowed:"), errors.get(0));
    }

    @Test
    void overloadsAreRefused(@TempDir Path dir) throws Exception {
        List<String> errors = errorsOf(dir, "package demo; @dev.jreact.api.ServerApi public interface Api { void a(); void a(int x); }");
        assertTrue(errors.get(0).contains("overloaded method a"), errors.toString());
    }

    @Test
    void mapsNeedStringKeys(@TempDir Path dir) throws Exception {
        List<String> errors = errorsOf(dir, "package demo; @dev.jreact.api.ServerApi public interface Api { java.util.Map<Integer, String> m(); }");
        assertTrue(errors.get(0).contains("String keys"), errors.toString());
    }

    @Test
    void genericRecordsAndNonPublicInterfacesAreRefused(@TempDir Path dir) throws Exception {
        List<String> generic = errorsOf(dir.resolve("a"), "package demo; @dev.jreact.api.ServerApi public interface Api { Box<String> b(); }",
                "demo.Box", "package demo; public record Box<T>(T value) {}");
        assertTrue(generic.get(0).contains("generic records"), generic.toString());
        List<String> hidden = errorsOf(dir.resolve("b"), "package demo; @dev.jreact.api.ServerApi interface Api { void a(); }");
        assertTrue(hidden.get(0).contains("must be public"), hidden.toString());
        List<String> notInterface = errorsOf(dir.resolve("c"), "package demo; @dev.jreact.api.ServerApi public class Api { }");
        assertTrue(notInterface.get(0).contains("goes on an interface"), notInterface.toString());
    }

    @Test
    void aRecordThatContainsItselfThroughAListWorks(@TempDir Path dir) throws Exception {
        Compiled c = compile(dir, Map.of(
                "demo.Node", "package demo; public record Node(String name, java.util.List<Node> children) {}",
                "demo.Api", "package demo; @dev.jreact.api.ServerApi public interface Api { Node tree(Node in); }",
                "demo.Impl", "package demo; public class Impl implements Api { public Node tree(Node in) { return in; } }"));
        assertEquals(List.of(), c.errors());
        RpcDispatcher d = (RpcDispatcher) c.loader().loadClass("demo.ApiDispatcher").getDeclaredConstructor().newInstance();
        Object impl = c.loader().loadClass("demo.Impl").getDeclaredConstructor().newInstance();
        Object in = Json.parse("{\"name\":\"root\",\"children\":[{\"name\":\"leaf\",\"children\":[]}]}");
        assertEquals("{\"name\":\"root\",\"children\":[{\"name\":\"leaf\",\"children\":[]}]}", Json.stringify(d.invoke(impl, "tree", new ArrayList<>(List.of(in)))));
    }
}

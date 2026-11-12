package jreact.compiler;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free test runner for the .jrx transpiler (run: ./build.sh test). */
public final class TranspilerTest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    private static final String BTN = """
            class Btn extends Component<Btn.Props> {
                public record Props(String label, int size, boolean primary, Runnable onPress) {}
            }
            class Panel extends Component<Panel.Props> {
                public record Props(@Deprecated String title, java.util.Map<String, Integer> data, List<Object> children) {}
            }
            """;

    public static void main(String[] args) {
        // ---- Java must pass through unchanged
        same("generics", "List<String> a = new ArrayList<>(); Map<String, List<Integer>> m = Map.of();");
        same("less-than", "if (a<b && c <d) return a<b; boolean x = i < n ? i<j : (k)<l; y = arr[0]<3;");
        same("return compare", "return i < n;");
        same("generic method", "public static <T> List<T> f() { return Collections.<T>emptyList(); }");
        same("strings", "String s = \"<div>\"; char c = '<'; String t = \"\"\"\n  <p>{x}</p>\n  \"\"\";");
        same("comments", "// <div>\n/* <span> */ int a = 1;");
        same("lambda compare", "list.removeIf(x -> x < 3);");
        same("annotation array", "@SuppressWarnings({\"unchecked\"}) void f() {}");

        // ---- host elements
        eq("simple element",
                "Element e = <div className=\"a\">Hi {name}!</div>;",
                "Element e = jreact.Jsx.el(\"div\", null, new jreact.Attrs().attr(\"className\", \"a\"), \"Hi \", name, \"!\");");
        eq("self closing + boolean attr",
                "x = <input disabled />;",
                "x = jreact.Jsx.el(\"input\", null, new jreact.Attrs().attr(\"disabled\", true));");
        eq("event handler",
                "x = <button onClick={e -> go()}>Go</button>;",
                "x = jreact.Jsx.el(\"button\", null, new jreact.Attrs().on(\"onClick\", e -> go()), \"Go\");");
        eq("key + spread + ref",
                "x = <div key={id} {...base} ref={r} />;",
                "x = jreact.Jsx.el(\"div\", id, new jreact.Attrs().spread(base).ref(r));");
        eq("return jsx", "return <br/>;", "return jreact.Jsx.el(\"br\", null, null);");
        eq("fragment",
                "x = <><a/><b/></>;",
                "x = jreact.Jsx.fragment(null, jreact.Jsx.el(\"a\", null, null), jreact.Jsx.el(\"b\", null, null));");
        eq("entities", "x = <p>&lt;tag&gt; &amp; &#65;&times;</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, \"<tag> & A×\");");
        eq("jsx comment child", "x = <p>{/* hidden */}ok</p>;", "x = jreact.Jsx.el(\"p\", null, null, \"ok\");");
        eq("element as attribute value", "x = <div icon=<i/> />;",
                "x = jreact.Jsx.el(\"div\", null, new jreact.Attrs().attr(\"icon\", jreact.Jsx.el(\"i\", null, null)));");
        eq("ternary branches", "x = ok ? <a/> : <b/>;",
                "x = ok ? jreact.Jsx.el(\"a\", null, null) : jreact.Jsx.el(\"b\", null, null);");
        eq("string with quotes in attr", "x = <p title='say \"hi\"' />;",
                "x = jreact.Jsx.el(\"p\", null, new jreact.Attrs().attr(\"title\", \"say \\\"hi\\\"\"));");

        // ---- JSX nested inside Java nested inside JSX
        eq("map with nested jsx",
                "x = <ul>{items.stream().map(i -> <li key={i}>{i < 3 ? \"a\" : \"b\"}</li>).toList()}</ul>;",
                "x = jreact.Jsx.el(\"ul\", null, null, items.stream().map(i -> jreact.Jsx.el(\"li\", i, null, i < 3 ? \"a\" : \"b\")).toList());");
        eq("braces in nested lambda",
                "x = <form onSubmit={e -> { if (a) { b(); } }} />;",
                "x = jreact.Jsx.el(\"form\", null, new jreact.Attrs().on(\"onSubmit\", e -> { if (a) { b(); } }));");
        eq("string with brace in container",
                "x = <p>{\"}\"}</p>;", "x = jreact.Jsx.el(\"p\", null, null, \"}\");");

        // ---- whitespace rules (React semantics)
        eq("multiline text",
                "x = <p>\n    Hello\n    world   \n</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, \"Hello world\"\n\n\n);");
        eq("inline spaces kept", "x = <p>a <b>b</b> c</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, \"a \", jreact.Jsx.el(\"b\", null, null, \"b\"), \" c\");");
        eq("a space between two expressions stays one space", "x = <p>{a} {b}</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, a, \" \", b);");
        eq("two spaces between expressions stay two (like React)", "x = <p>{a}  {b}</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, a, \"  \", b);");
        eq("a newline between expressions adds no text", "x = <p>{a}\n  {b}</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, a, \nb);");     // the line break stays so line numbers match
        eq("whitespace-only lines dropped", "x = <p>\n  <a/>\n  <b/>\n</p>;",
                "x = jreact.Jsx.el(\"p\", null, null, \n" +
                "jreact.Jsx.el(\"a\", null, null), \n" +
                "jreact.Jsx.el(\"b\", null, null)\n);");

        // ---- components (typed Props)
        eq("component props in record order + defaults",
                BTN + "x = <Btn primary onPress={() -> run()} label=\"OK\" />;",
                BTN + "x = jreact.Jsx.c(Btn.class, Btn::new, null, new Btn.Props(\"OK\", 0, true, () -> run()));");
        eq("component key + children",
                BTN + "x = <Panel key=\"p\" title=\"T\"><a/>txt</Panel>;",
                BTN + "x = jreact.Jsx.c(Panel.class, Panel::new, \"p\", new Panel.Props(\"T\", null, jreact.Jsx.children(jreact.Jsx.el(\"a\", null, null), \"txt\")));");
        eq("component without children", BTN + "x = <Panel />;",
                BTN + "x = jreact.Jsx.c(Panel.class, Panel::new, null, new Panel.Props(null, null, java.util.List.of()));".replace("java.util.List.of()", "jreact.Jsx.children()"));

        // ---- line preservation
        String multi = "class A {\n  Element r() {\n    return (\n      <div\n        a=\"1\"\n        b={2}>\n        <span>x</span>\n        {/*\n          c\n        */}\n      </div>\n    );\n  }\n}\n";
        check("line count preserved", lines(t(multi)) == lines(multi), t(multi));

        // ---- errors
        error("mismatched tag", "x = <div>\n  <span>\n</div>;", 3, 1, "expected </span>");
        error("unclosed tag", "x = <div>", 1, 5, "unclosed <div>");
        error("unknown prop", BTN + "x = <Btn colour=\"red\" />;", 7, 10, "has no prop 'colour'");
        error("children not accepted", BTN + "x = <Btn>hi</Btn>;", 7, 5, "does not accept children");
        error("unknown component with props", "x = <Ghost a={1} />;", 1, 5, "unknown component <Ghost>");
        error("string event handler", "x = <a onClick=\"go()\" />;", 1, 8, "must be an {expression}");
        error("empty expression", "x = <a href={} />;", 1, 13, "empty expression");
        error("stray brace", "x = <p>a } b</p>;", 1, 10, "unexpected '}'");
        error("unclosed container", "x = <p>{foo(</p>;", 1, 8, "unclosed '{'");

        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("FAIL " + f));
            System.exit(1);
        }
    }

    private static String t(String src) {
        PropsIndex index = new PropsIndex();
        index.scan(src);
        return new JsxTranspiler("Test.jrx", src, index).transpile();
    }

    private static void same(String name, String src) { eq(name, src, src); }

    private static void eq(String name, String src, String expected) {
        try {
            String got = t(src);
            check(name, got.equals(expected), "\n  expected: " + expected + "\n  got:      " + got);
        } catch (CompileError e) {
            check(name, false, "unexpected error: " + e.format());
        }
    }

    private static void error(String name, String src, int line, int col, String msgPart) {
        try {
            String got = t(src);
            check(name, false, "expected an error, got: " + got);
        } catch (CompileError e) {
            boolean ok = e.line() == line && e.col() == col && e.getMessage().contains(msgPart);
            check(name, ok, "expected " + line + ":" + col + " '" + msgPart + "', got " + e.line() + ":" + e.col() + " '" + e.getMessage() + "'");
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("ok   " + name); }
        else failures.add(name + ": " + detail);
    }

    private static int lines(String s) { return s.split("\n", -1).length; }
}

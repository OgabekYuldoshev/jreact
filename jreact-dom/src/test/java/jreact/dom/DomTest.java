package jreact.dom;

import jreact.Attrs;
import jreact.Component;
import jreact.Element;
import jreact.Jsx;
import jreact.State;
import jreact.scheduler.ManualSchedulerHost;
import jreact.scheduler.Priority;
import jreact.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Dependency-free tests of jreact-dom against an in-memory DOM (run: ./build.sh test). */
public final class DomTest {

    // ================================================================ fake DOM

    static final class FNode {
        final String tag;               // null = text node
        String text;
        final Map<String, String> attrs = new LinkedHashMap<>();
        final Map<String, Object> props = new LinkedHashMap<>();
        final Map<String, String> style = new LinkedHashMap<>();
        final List<FNode> children = new ArrayList<>();
        FNode parent;
        Object data;
        int childrenWhenValueSet = -1;

        FNode(String tag, String text) { this.tag = tag; this.text = text; }

        String html() {
            if (tag == null) return text;
            StringBuilder sb = new StringBuilder("<").append(tag);
            attrs.forEach((k, v) -> sb.append(' ').append(k).append(v.isEmpty() ? "" : "=\"" + v + "\""));
            if (!style.isEmpty()) {
                sb.append(" style=\"");
                style.forEach((k, v) -> sb.append(k).append(':').append(v).append(';'));
                sb.append('"');
            }
            sb.append('>');
            children.forEach(c -> sb.append(c.html()));
            return sb.append("</").append(tag).append('>').toString();
        }
    }

    static final class FakeDom implements DomOps<FNode> {
        final Map<String, Consumer<NativeEvent<FNode>>> listeners = new LinkedHashMap<>();
        int listenerRegistrations;

        public FNode createElement(String tag) { return new FNode(tag, null); }
        public FNode createTextNode(String text) { return new FNode(null, text); }
        public void setAttribute(FNode e, String n, String v) { e.attrs.put(n, v); }
        public void removeAttribute(FNode e, String n) { e.attrs.remove(n); }
        public void setProperty(FNode e, String n, Object v) {
            e.props.put(n, v);
            if (n.equals("value")) e.childrenWhenValueSet = e.children.size();
        }
        public void setStyleProperty(FNode e, String n, String v) { if (v == null) e.style.remove(n); else e.style.put(n, v); }
        public void setTextContent(FNode t, String text) { t.text = text; }
        public void appendChild(FNode p, FNode c) { detach(c); p.children.add(c); c.parent = p; }
        public void insertBefore(FNode p, FNode c, FNode before) { detach(c); p.children.add(p.children.indexOf(before), c); c.parent = p; }
        public void removeChild(FNode p, FNode c) { detach(c); }
        public FNode parentNode(FNode n) { return n.parent; }
        public Object getData(FNode n) { return n.data; }
        public void setData(FNode n, Object d) { n.data = d; }
        public void addEventListener(FNode container, String type, Consumer<NativeEvent<FNode>> l) {
            listenerRegistrations++;
            listeners.put(type, l);
        }

        private static void detach(FNode c) { if (c.parent != null) { c.parent.children.remove(c); c.parent = null; } }

        /** Dispatches like the browser would: the root listener receives an event that targets `target`. */
        FakeEvent fire(String type, FNode target, String value, boolean checked) {
            FakeEvent e = new FakeEvent(type, target, value, checked);
            Consumer<NativeEvent<FNode>> l = listeners.get(type);
            if (l != null) l.accept(e);
            return e;
        }
        FakeEvent fire(String type, FNode target) { return fire(type, target, null, false); }
    }

    record FakeEvent(String type, FNode target, String value, boolean checked, boolean[] prevented) implements NativeEvent<FNode> {
        FakeEvent(String type, FNode target, String value, boolean checked) { this(type, target, value, checked, new boolean[1]); }
        public String targetTag() { return target.tag; }
        public String targetType() { return target.attrs.get("type"); }
        public void preventDefault() { prevented[0] = true; }
        boolean wasPrevented() { return prevented[0]; }
    }

    static final class Env {
        final FakeDom dom = new FakeDom();
        final ManualSchedulerHost clock = new ManualSchedulerHost();
        final FNode container = new FNode("root", null);
        final DomRoot<FNode> root = DomRoot.create(dom, container, clock);

        void flush() { clock.runUntilIdle(); }
        void render(Element e) { root.render(e); flush(); }
        String html() { StringBuilder sb = new StringBuilder(); container.children.forEach(c -> sb.append(c.html())); return sb.toString(); }
        FNode first() { return container.children.get(0); }
    }

    // ================================================================ components

    static final List<String> log = new ArrayList<>();

    static Element el(String tag, Attrs attrs, Object... children) { return Jsx.el(tag, null, attrs, children); }

    static class Counter extends Component<Counter.Props> {
        record Props() {}
        public Element render(Props p) {
            State<Integer> n = useState(0);
            return el("button", new Attrs().on("onClick", e -> n.update(x -> x + 1)), "n=", n.get());
        }
        static Element make() { return Jsx.c(Counter.class, Counter::new, null, new Props()); }
    }

    // ================================================================ tests

    public static void main(String[] args) {
        test("attribute rules: aliases, booleans, numbers, removal", () -> {
            Env e = new Env();
            e.render(el("label", new Attrs().attr("className", "a b").attr("htmlFor", "x").attr("tabIndex", 3)
                    .attr("data-id", 7).attr("disabled", true).attr("hidden", false).attr("title", null), "t"));
            eq("<label class=\"a b\" for=\"x\" tabindex=\"3\" data-id=\"7\" disabled>t</label>", e.html());
            e.render(el("label", new Attrs().attr("className", "c").attr("htmlFor", "x"), "t"));
            eq("<label class=\"c\" for=\"x\">t</label>", e.html());
        });

        test("value and checked are DOM properties, not attributes", () -> {
            Env e = new Env();
            e.render(el("input", new Attrs().attr("type", "checkbox").attr("value", "v").attr("checked", true)));
            eq("<input type=\"checkbox\"></input>", e.html());
            eq("v", e.first().props.get("value"));
            eq(true, e.first().props.get("checked"));
            e.render(el("input", new Attrs().attr("type", "checkbox").attr("value", null).attr("checked", false)));
            eq("", e.first().props.get("value"));
            eq(false, e.first().props.get("checked"));
        });

        test("select value is applied after its options exist", () -> {
            Env e = new Env();
            e.render(el("select", new Attrs().attr("value", "b"),
                    el("option", new Attrs().attr("value", "a"), "A"),
                    el("option", new Attrs().attr("value", "b"), "B")));
            eq("b", e.first().props.get("value"));
            eq(2, e.first().childrenWhenValueSet);
        });

        test("style object: kebab-case, px for numbers, unitless, update and remove", () -> {
            Env e = new Env();
            Map<String, Object> s1 = new LinkedHashMap<>();
            s1.put("backgroundColor", "red"); s1.put("width", 10); s1.put("opacity", 1); s1.put("margin", 0); s1.put("--gap", 4);
            e.render(el("div", new Attrs().attr("style", s1)));
            eq("<div style=\"background-color:red;width:10px;opacity:1;margin:0;--gap:4;\"></div>", e.html());
            Map<String, Object> s2 = new LinkedHashMap<>();
            s2.put("backgroundColor", "blue"); s2.put("width", null);
            e.render(el("div", new Attrs().attr("style", s2)));
            eq("<div style=\"background-color:blue;\"></div>", e.html());
            e.render(el("div", new Attrs()));
            eq("<div></div>", e.html());
        });

        // ---------------------------------------------------------------- security

        test("javascript: URLs are blocked, also with hidden control characters and capitals", () -> {
            Env e = new Env();
            List<String> warnings = new ArrayList<>();
            e.root.host().setWarningHandler(warnings::add);
            e.render(el("div", null,
                    el("a", new Attrs().attr("href", "javascript:alert(1)"), "a"),
                    el("a", new Attrs().attr("href", "  JaVa\tScRiPt:alert(1)"), "b"),
                    el("a", new Attrs().attr("href", "vbscript:x"), "c"),
                    el("form", new Attrs().attr("action", "javascript:x")),
                    el("button", new Attrs().attr("formAction", "javascript:x"), "d"),
                    el("a", new Attrs().attr("href", "https://example.com/?q=javascript:1"), "ok"),
                    el("a", new Attrs().attr("href", "/relative#x"), "ok2")));
            String html = e.html();
            check(!html.replace("https://example.com/?q=javascript:1", "").toLowerCase().contains("script:"), "dangerous URL survived: " + html);
            check(html.contains("href=\"https://example.com/?q=javascript:1\"") && html.contains("href=\"/relative#x\""), "safe URLs changed: " + html);
            eq(5, html.split("about:blank", -1).length - 1);   // 3 hrefs, action, formaction
            check(!warnings.isEmpty(), "no warning");
            // updates are checked too
            e.render(el("div", null, el("a", new Attrs().attr("href", "/safe"), "x")));
            e.render(el("div", null, el("a", new Attrs().attr("href", "javascript:evil()"), "x")));
            check(e.html().contains("about:blank"), "update bypassed the check: " + e.html());
        });

        test("inline handler attributes and bad attribute names are ignored", () -> {
            Env e = new Env();
            List<String> warnings = new ArrayList<>();
            e.root.host().setWarningHandler(warnings::add);
            e.render(el("img", new Attrs().attr("src", "x.png").attr("onerror", "alert(1)").attr("onclick", "x()")
                    .attr("x\" onmouseover=\"alert(1)", "v").attr("bad name", "v").attr("", "v").attr("data-ok", "1")));
            eq("<img src=\"x.png\" data-ok=\"1\"></img>", e.html());
            eq(5, warnings.size());   // onerror, onclick, the injected name, "bad name", ""
        });

        test("script elements and invalid tag names are refused", () -> {
            Env e = new Env();
            boolean threw = false;
            try { e.render(el("script", null, "alert(1)")); } catch (RuntimeException ex) { threw = true; }
            check(threw, "<script> was created");
            check(e.container.children.isEmpty() || !e.html().contains("script"), "script in DOM: " + e.html());
            Env f = new Env();
            threw = false;
            try { f.render(el("img src=x onerror=alert(1)", null)); } catch (RuntimeException ex) { threw = true; }
            check(threw, "invalid tag name accepted");
        });

        test("text children and attribute values are never HTML", () -> {
            Env e = new Env();
            e.render(el("p", new Attrs().attr("title", "\"><img src=x onerror=alert(1)>"), "<img src=x onerror=alert(1)>"));
            check(e.first().children.size() == 1 && e.first().children.get(0).tag == null, "text became an element");
        });

        test("ServerRenderer escapes text and attributes, blocks javascript: URLs, refuses bad tags", () -> {
            String html = jreact.ServerRenderer.renderToString(el("div", new Attrs().attr("title", "\"><script>x</script>")
                    .attr("onclick", "x()").attr("bad name", "1").attr("style", Map.of("color", "red; position: fixed")),
                    "<b>&</b>", el("a", new Attrs().attr("href", "javascript:alert(1)"), "go"),
                    el("i", new Attrs().attr("style", Map.of("backgroundColor", "#fff", "width", 10)))));
            eq("<div title=\"&quot;&gt;&lt;script&gt;x&lt;/script&gt;\">&lt;b&gt;&amp;&lt;/b&gt;<a href=\"about:blank\">go</a>"
                    + "<i style=\"background-color: #fff; width: 10px;\"></i></div>", html);
            boolean threw = false;
            try { jreact.ServerRenderer.renderToString(el("script", null, "x")); } catch (IllegalArgumentException ex) { threw = true; }
            check(threw, "SSR rendered <script>");
        });

        test("style string goes to the style attribute", () -> {
            Env e = new Env();
            e.render(el("div", new Attrs().attr("style", "color: red")));
            eq("<div style=\"color: red\"></div>", e.html());
        });

        test("one root listener per event type, however many handlers", () -> {
            Env e = new Env();
            e.render(el("div", null,
                    el("button", new Attrs().on("onClick", x -> {})), el("button", new Attrs().on("onClick", x -> {})),
                    el("input", new Attrs().on("onInput", x -> {}))));
            eq(2, e.dom.listenerRegistrations);
        });

        test("click -> setState -> DOM updated through the scheduler", () -> {
            Env e = new Env();
            e.render(Counter.make());
            FNode button = e.first();
            eq("<button>n=0</button>", e.html());
            e.dom.fire("click", button);
            eq("<button>n=0</button>", e.html());       // batched: nothing yet
            e.flush();
            eq("<button>n=1</button>", e.html());
            check(button == e.first(), "button was replaced");
        });

        test("events bubble inside-out and stopPropagation stops them", () -> {
            Env e = new Env();
            log.clear();
            e.render(el("div", new Attrs().on("onClick", x -> log.add("outer")),
                    el("button", new Attrs().on("onClick", x -> log.add("inner"))),
                    el("button", new Attrs().on("onClick", x -> { log.add("stopper"); x.stopPropagation(); }))));
            e.dom.fire("click", e.first().children.get(0));
            eq("[inner, outer]", log.toString());
            log.clear();
            e.dom.fire("click", e.first().children.get(1));
            eq("[stopper]", log.toString());
        });

        test("an element with only text content still gets its own handler", () -> {
            Env e = new Env();
            log.clear();
            e.render(el("p", new Attrs().on("onClick", x -> log.add("p")), "text"));
            e.dom.fire("click", e.first());
            eq("[p]", log.toString());
        });

        test("preventDefault reaches the native event", () -> {
            Env e = new Env();
            e.render(el("form", new Attrs().on("onSubmit", x -> x.preventDefault())));
            FakeEvent ev = e.dom.fire("submit", e.first());
            check(ev.wasPrevented(), "not prevented");
        });

        test("handlers follow re-renders: latest closure runs, removed handlers stop", () -> {
            Env e = new Env();
            log.clear();
            e.render(el("button", new Attrs().on("onClick", x -> log.add("v1"))));
            e.render(el("button", new Attrs().on("onClick", x -> log.add("v2"))));
            e.dom.fire("click", e.first());
            eq("[v2]", log.toString());
            e.render(el("button", new Attrs()));
            e.dom.fire("click", e.first());
            eq("[v2]", log.toString());
        });

        test("onChange: text input fires on input with the value, checkbox on change with checked", () -> {
            Env e = new Env();
            log.clear();
            e.render(el("div", null,
                    el("input", new Attrs().attr("type", "text")
                            .on("onChange", x -> log.add("text:" + x.value()))
                            .on("onInput", x -> log.add("input:" + x.value()))),
                    el("input", new Attrs().attr("type", "checkbox").on("onChange", x -> log.add("box:" + x.checked())))));
            FNode text = e.first().children.get(0), box = e.first().children.get(1);
            e.dom.fire("input", text, "hi", false);
            e.dom.fire("change", text, "hi", false);        // blur-time change of a text input: ignored
            e.dom.fire("change", box, null, true);
            eq("[input:hi, text:hi, box:true]", log.toString());
        });

        test("camelCase handler names map to native types", () -> {
            Env e = new Env();
            log.clear();
            e.render(el("input", new Attrs().on("onKeyDown", x -> log.add("kd")).on("onDoubleClick", x -> log.add("dbl"))
                    .on("onBlur", x -> log.add("blur"))));
            e.dom.fire("keydown", e.first()); e.dom.fire("dblclick", e.first()); e.dom.fire("focusout", e.first());
            eq("[kd, dbl, blur]", log.toString());
        });

        test("events run at their priority: input is urgent, mouse move is not", () -> {
            FakeDom dom = new FakeDom();
            FNode container = dom.createElement("root"), button = dom.createElement("button");
            dom.appendChild(container, button);
            DomHost<FNode> host = new DomHost<>(dom, container);
            List<Priority> seen = new ArrayList<>();
            host.setUpdateRunner((p, body) -> { seen.add(p); body.run(); });
            host.finalizeInitialChildren(button, new Attrs().on("onClick", x -> {}).on("onMouseMove", x -> {}));
            dom.fire("click", button);
            dom.fire("mousemove", button);
            eq("[USER_BLOCKING, NORMAL]", seen.toString());
        });

        test("an urgent click is not stuck behind a paused transition", () -> {
            Env e = new Env();
            class Slow extends Component<Slow.Props> {
                record Props(int cost) {}
                public Element render(Props p) { e.clock.advance(p.cost()); return el("i", null, "s"); }
            }
            class Page extends Component<Page.Props> {
                record Props() {}
                public Element render(Props p) {
                    State<Integer> page = useState(0);
                    pageState = page;
                    List<Object> kids = new ArrayList<>();
                    for (int i = 0; i < 10; i++) kids.add(Jsx.c(Slow.class, Slow::new, "k" + i, new Slow.Props(3)));
                    return el("div", null, el("b", null, page.get()), el("div", null, kids), Counter.make());
                }
            }
            e.render(Jsx.c(Page.class, Page::new, null, new Page.Props()));
            e.root.startTransition(() -> pageState.set(1));
            e.clock.step();
            check(e.html().startsWith("<div><b>0</b>"), "transition committed too early: " + e.html());
            e.dom.fire("click", e.first().children.get(2));     // urgent click on the counter
            e.clock.step();
            check(e.html().startsWith("<div><b>1</b>"), "transition not finished by the urgent task: " + e.html());
            e.flush();
            check(e.html().endsWith("<button>n=1</button></div>"), "click was lost: " + e.html());
        });

        test("keyed list reorder moves DOM nodes", () -> {
            Env e = new Env();
            e.render(list("a", "b", "c"));
            FNode a = e.first().children.get(0), cc = e.first().children.get(2);
            e.render(list("c", "a", "b"));
            eq("<ul><li>c</li><li>a</li><li>b</li></ul>", e.html());
            check(e.first().children.get(0) == cc && e.first().children.get(1) == a, "nodes were recreated");
        });

        test("unmount empties the container", () -> {
            Env e = new Env();
            e.render(Counter.make());
            e.root.unmount();
            eq("", e.html());
        });

        test("EventLoopSchedulerHost drives the scheduler through the event loop primitives", () -> {
            FakeTimers t = new FakeTimers();
            Scheduler s = new Scheduler(new EventLoopSchedulerHost(t));
            List<String> out = new ArrayList<>();
            s.scheduleCallback(Priority.NORMAL, d -> { out.add("now"); return null; });
            eq(1, t.tasks.size());
            t.runTasks();
            eq("[now]", out.toString());

            s.scheduleCallback(Priority.NORMAL, d -> { out.add("later"); return null; }, 100);
            eq(0, t.tasks.size());
            eq("[100]", t.timeoutDelays.toString());
            t.now = 100;
            t.fireTimeouts();
            t.runTasks();
            eq("[now, later]", out.toString());
        });

        test("EventLoopSchedulerHost cancels its timeout", () -> {
            FakeTimers t = new FakeTimers();
            EventLoopSchedulerHost host = new EventLoopSchedulerHost(t);
            host.requestHostTimeout(() -> {}, 50);
            eq(1, t.timeouts.size());
            host.cancelHostTimeout();
            eq(0, t.timeouts.size());
        });

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        if (!failures.isEmpty()) System.exit(1);
    }

    static State<Integer> pageState;

    static Element list(String... keys) {
        List<Object> items = new ArrayList<>();
        for (String k : keys) items.add(Jsx.el("li", k, null, k));
        return Jsx.el("ul", null, null, items);
    }

    static final class FakeTimers implements Timers {
        long now;
        final List<Runnable> tasks = new ArrayList<>();
        final Map<Integer, Runnable> timeouts = new LinkedHashMap<>();
        final List<Long> timeoutDelays = new ArrayList<>();
        private int nextHandle = 1;

        public long now() { return now; }
        public void postTask(Runnable task) { tasks.add(task); }
        public Object setTimeout(Runnable task, long delayMs) { timeouts.put(nextHandle, task); timeoutDelays.add(delayMs); return nextHandle++; }
        public void clearTimeout(Object handle) { timeouts.remove(handle); }

        void runTasks() { while (!tasks.isEmpty()) tasks.remove(0).run(); }
        void fireTimeouts() { new ArrayList<>(timeouts.values()).forEach(Runnable::run); timeouts.clear(); }
    }

    // ================================================================ mini harness

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    static void test(String name, Runnable body) {
        try { body.run(); passed++; }
        catch (Throwable t) { failures.add(name + ": " + t); t.printStackTrace(System.out); }
    }

    static void eq(Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError("expected: " + expected + "\n  actual: " + actual);
        }
    }

    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}

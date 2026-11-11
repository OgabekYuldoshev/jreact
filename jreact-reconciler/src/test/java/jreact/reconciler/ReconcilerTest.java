package jreact.reconciler;

import jreact.Attrs;
import jreact.Component;
import jreact.Context;
import jreact.Element;
import jreact.Event;
import jreact.EventHandler;
import jreact.Jsx;
import jreact.MemoComponent;
import jreact.Ref;
import jreact.ServerRenderer;
import jreact.State;
import jreact.scheduler.ManualSchedulerHost;
import jreact.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Dependency-free tests of the reconciler against an in-memory host (run: ./build.sh test). */
public final class ReconcilerTest {

    // ================================================================ in-memory host

    static final class Node {
        final String tag;          // null for text
        String text;
        final Map<String, Object> attrs = new LinkedHashMap<>();
        final Map<String, EventHandler> events = new LinkedHashMap<>();
        final List<Node> children = new ArrayList<>();
        Node parent;

        Node(String tag, String text) { this.tag = tag; this.text = text; }

        String html() {
            if (tag == null) return text;
            StringBuilder sb = new StringBuilder("<").append(tag);
            attrs.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
            sb.append('>');
            for (Node c : children) sb.append(c.html());
            return sb.append("</").append(tag).append('>').toString();
        }

        void click() { events.get("onClick").handle(new FakeEvent("click")); }
    }

    record FakeEvent(String type) implements Event {
        public String value() { return null; }
        public boolean checked() { return false; }
        public void preventDefault() {}
        public void stopPropagation() {}
    }

    static final class Host implements jreact.reconciler.HostConfig<Node> {
        int created, removed;

        public Node createInstance(String tag) { created++; return new Node(tag, null); }
        public void finalizeInitialChildren(Node n, Attrs initial) {
            n.attrs.putAll(initial.values());
            n.events.putAll(initial.events());
        }
        public Node createTextInstance(String text) { created++; return new Node(null, text); }
        public void appendInitialChild(Node p, Node c) { appendChild(p, c); }
        public void appendChild(Node p, Node c) { detach(c); p.children.add(c); c.parent = p; }
        public void insertBefore(Node p, Node c, Node before) {
            detach(c);
            p.children.add(p.children.indexOf(before), c);
            c.parent = p;
        }
        public void removeChild(Node p, Node c) { removed++; detach(c); }
        public void commitUpdate(Node n, AttrsDiff d) {
            d.attrs().forEach((k, v) -> { if (v == null) n.attrs.remove(k); else n.attrs.put(k, v); });
            d.events().forEach((k, h) -> { if (h == null) n.events.remove(k); else n.events.put(k, h); });
        }
        public void commitTextUpdate(Node n, String o, String t) { n.text = t; }

        private static void detach(Node c) { if (c.parent != null) { c.parent.children.remove(c); c.parent = null; } }
    }

    static final class Env {
        final Host host = new Host();
        final ManualSchedulerHost clock = new ManualSchedulerHost();
        final Reconciler<Node> reconciler = new Reconciler<>(host, new Scheduler(clock));
        final Node container = new Node("root", null);
        final Root<Node> root = reconciler.createRoot(container);

        final List<String> warnings = new ArrayList<>();

        Env() {
            Slow.clock = clock;
            reconciler.setWarningHandler(warnings::add);
        }

        /** Like the browser event loop: run every macrotask the scheduler asked for. */
        void flush() { clock.runUntilIdle(); }
        void render(Element e) { root.render(e); flush(); }
        String html() { StringBuilder sb = new StringBuilder(); container.children.forEach(c -> sb.append(c.html())); return sb.toString(); }
        Node first() { return container.children.get(0); }
    }

    // ================================================================ test components

    static final List<String> log = new ArrayList<>();
    static int renders;

    static <C extends Component<P>, P> Element c(Class<C> type, Supplier<C> factory, Object key, P props) {
        return Jsx.c(type, factory, key, props);
    }

    static Element el(String tag, Object... children) { return Jsx.el(tag, null, null, children); }

    static class Counter extends Component<Counter.Props> {
        record Props(String label) {}
        static State<Integer> last;
        public Element render(Props p) {
            renders++;
            State<Integer> n = useState(0);
            last = n;
            return Jsx.el("button", null, new Attrs().on("onClick", e -> n.update(x -> x + 1)), p.label(), ":", n.get());
        }
        static Element make(String label) { return c(Counter.class, Counter::new, null, new Props(label)); }
    }

    static class Item extends Component<Item.Props> {
        record Props(String label) {}
        public Element render(Props p) {
            State<Integer> n = useState(0);
            return Jsx.el("button", null, new Attrs().on("onClick", e -> n.update(x -> x + 1)), p.label(), n.get());
        }
        static Element make(String label) { return c(Item.class, Item::new, label, new Props(label)); }
    }

    static class Logger extends Component<Logger.Props> {
        record Props(String name, String dep) {}
        public Element render(Props p) {
            useEffect(() -> { log.add("mount " + p.name() + ":" + p.dep()); return () -> log.add("cleanup " + p.name() + ":" + p.dep()); }, p.dep());
            return Jsx.el("i", null, null, p.name());
        }
        static Element make(String name, String dep) { return c(Logger.class, Logger::new, null, new Props(name, dep)); }
    }

    /** Burns `cost` ms of fake time per render, like an expensive component. */
    static class Slow extends Component<Slow.Props> {
        record Props(String label, int cost) {}
        static ManualSchedulerHost clock;
        public Element render(Props p) {
            clock.advance(p.cost());
            return Jsx.el("i", null, null, p.label());
        }
    }

    /** `page` drives ten slow children, `flag` is a cheap unrelated state. */
    static class Page extends Component<Page.Props> {
        record Props() {}
        static State<Integer> page;
        static State<Boolean> flag;
        public Element render(Props p) {
            State<Integer> pg = useState(0);
            State<Boolean> fl = useState(false);
            page = pg; flag = fl;
            List<Object> kids = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                kids.add(c(Slow.class, Slow::new, "k" + i, new Slow.Props("p" + pg.get() + (fl.get() ? "!" : ""), 3)));
            }
            return Jsx.el("div", null, null, kids);
        }
        static Element make() { return c(Page.class, Page::new, null, new Props()); }
        static String html(String label) { return "<div>" + ("<i>" + label + "</i>").repeat(10) + "</div>"; }
    }

    // ---- context and memo probes

    static final Context<String> THEME = Context.create("light");
    static final Context<Integer> SIZE = Context.create(1);
    static int themedRenders, sizedRenders, midRenders, memoRenders, plainRenders;
    static State<Integer> themedState, tickState;

    static void resetCounts() { themedRenders = sizedRenders = midRenders = memoRenders = plainRenders = 0; }

    static class Themed extends Component<Themed.Props> {
        record Props() {}
        public Element render(Props p) {
            themedRenders++;
            State<Integer> n = useState(0);
            themedState = n;
            return Jsx.el("p", null, null, useContext(THEME), "/", n.get());
        }
        static Element make() { return c(Themed.class, Themed::new, null, new Props()); }
    }

    static class Sized extends Component<Sized.Props> {
        record Props() {}
        public Element render(Props p) { sizedRenders++; return Jsx.el("s", null, null, useContext(SIZE)); }
        static Element make() { return c(Sized.class, Sized::new, null, new Props()); }
    }

    /** Memo component between a provider and its consumers. */
    static class Mid extends MemoComponent<Mid.Props> {
        record Props(String x) {}
        public Element render(Props p) { midRenders++; return Jsx.el("div", null, null, Themed.make(), Sized.make()); }
        static Element make(String x) { return c(Mid.class, Mid::new, null, new Props(x)); }
    }

    static class MemoProbe extends MemoComponent<MemoProbe.Props> {
        record Props(String label) {}
        static State<Integer> own;
        public Element render(Props p) {
            memoRenders++;
            State<Integer> n = useState(0);
            own = n;
            return Jsx.el("b", null, null, p.label(), n.get());
        }
        static Element make(String label) { return c(MemoProbe.class, MemoProbe::new, null, new Props(label)); }
    }

    static class PlainProbe extends Component<PlainProbe.Props> {
        record Props(String label) {}
        public Element render(Props p) { plainRenders++; return Jsx.el("i", null, null, p.label()); }
        static Element make(String label) { return c(PlainProbe.class, PlainProbe::new, null, new Props(label)); }
    }

    /** Re-renders on `tick`, and passes the same props to a memo and a plain child. */
    static class TickParent extends Component<TickParent.Props> {
        record Props() {}
        public Element render(Props p) {
            State<Integer> tick = useState(0);
            tickState = tick;
            return Jsx.el("div", null, null, tick.get(), MemoProbe.make("a"), PlainProbe.make("a"));
        }
        static Element make() { return c(TickParent.class, TickParent::new, null, new Props()); }
    }

    /** Memo that only looks at `a`. */
    static class CustomMemo extends MemoComponent<CustomMemo.Props> {
        record Props(String a, String b) {}
        @Override protected boolean arePropsEqual(Props prev, Props next) { return prev.a().equals(next.a()); }
        public Element render(Props p) { memoRenders++; return Jsx.el("u", null, null, p.a(), p.b()); }
        static Element make(String a, String b) { return c(CustomMemo.class, CustomMemo::new, null, new Props(a, b)); }
    }

    static class Memo extends Component<Memo.Props> {
        record Props(int a, int b) {}
        static int computes;
        static final List<Object> refs = new ArrayList<>();
        public Element render(Props p) {
            Integer v = useMemo(() -> { computes++; return p.a() * 10; }, p.a());
            refs.add(useRef("x"));
            return Jsx.el("u", null, null, v + "/" + p.b());
        }
    }

    // ================================================================ tests

    public static void main(String[] args) {
        test("mounts a host tree", () -> {
            Env e = new Env();
            e.render(Jsx.el("div", null, new Attrs().attr("className", "a"), "hi ", Jsx.el("b", null, null, "x")));
            eq("<div className=\"a\">hi <b>x</b></div>", e.html());
        });

        test("renders fragments, lists, null/false/numbers", () -> {
            Env e = new Env();
            e.render(Jsx.el("p", null, null, "a", null, false, 3, List.of("x", "y"), Jsx.fragment(null, "!", "?")));
            eq("<p>a3xy!?</p>", e.html());
        });

        test("setState re-renders in place without recreating nodes", () -> {
            Env e = new Env();
            e.render(Counter.make("n"));
            Node button = e.first();
            eq("<button>n:0</button>", e.html());
            int created = e.host.created;
            button.click(); e.flush();
            eq("<button>n:1</button>", e.html());
            check(button == e.first(), "button node was replaced");
            eq(created, e.host.created);
        });

        test("updates in one handler are batched into one render", () -> {
            Env e = new Env();
            e.render(Counter.make("n"));
            renders = 0;
            Counter.last.update(x -> x + 1);
            Counter.last.update(x -> x + 1);
            Counter.last.set(10);
            e.flush();
            eq(1, renders);
            eq("<button>n:10</button>", e.html());
        });

        test("setState to an equal value is skipped eagerly", () -> {
            Env e = new Env();
            e.render(Counter.make("n"));
            renders = 0;
            Counter.last.set(0);
            check(!e.clock.hasPending(), "work was scheduled for an unchanged state");
            e.flush();
            eq(0, renders);
        });

        test("state change re-renders only the affected component", () -> {
            Env e = new Env();
            class Parent extends Component<Parent.Props> {
                record Props() {}
                public Element render(Props p) {
                    log.add("parent");
                    return Jsx.el("div", null, null, Counter.make("a"), Counter.make("b"));
                }
            }
            log.clear();
            e.render(c(Parent.class, Parent::new, null, new Parent.Props()));
            renders = 0; log.clear();
            e.first().children.get(1).click(); e.flush();
            eq("<div><button>a:0</button><button>b:1</button></div>", e.html());
            eq(1, renders);                         // only `b` rendered
            eq(0, log.size());                      // parent bailed out
        });

        test("keyed reorder moves nodes instead of recreating them", () -> {
            Env e = new Env();
            e.render(list("a", "b", "c"));
            Node ul = e.first();
            Node a = ul.children.get(0), b = ul.children.get(1), cc = ul.children.get(2);
            int created = e.host.created;
            e.render(list("c", "a", "b"));
            eq("<ul><li>c</li><li>a</li><li>b</li></ul>", e.html());
            check(ul.children.get(0) == cc && ul.children.get(1) == a && ul.children.get(2) == b, "nodes were not reused");
            eq(created, e.host.created);
        });

        test("keyed insert in the middle and remove", () -> {
            Env e = new Env();
            e.render(list("a", "c"));
            Node ul = e.first();
            Node a = ul.children.get(0), cc = ul.children.get(1);
            e.render(list("a", "b", "c"));
            eq("<ul><li>a</li><li>b</li><li>c</li></ul>", e.html());
            check(ul.children.get(0) == a && ul.children.get(2) == cc, "neighbours were recreated");
            e.render(list("c"));
            eq("<ul><li>c</li></ul>", e.html());
            check(ul.children.get(0) == cc, "survivor was recreated");
        });

        test("keyed components keep their state when reordered", () -> {
            Env e = new Env();
            e.render(el("div", Item.make("a"), Item.make("b")));
            e.first().children.get(1).click(); e.flush();
            eq("<div><button>a0</button><button>b1</button></div>", e.html());
            e.render(el("div", Item.make("b"), Item.make("a")));
            eq("<div><button>b1</button><button>a0</button></div>", e.html());
        });

        test("a different element type replaces the subtree", () -> {
            Env e = new Env();
            e.render(el("div", "x"));
            Node old = e.first();
            e.render(el("span", "x"));
            eq("<span>x</span>", e.html());
            check(old.parent == null, "old node still attached");
        });

        test("attribute diff adds, changes and removes", () -> {
            Env e = new Env();
            e.render(Jsx.el("div", null, new Attrs().attr("a", "1").attr("b", "2"), "t"));
            e.render(Jsx.el("div", null, new Attrs().attr("a", "9").attr("c", "3"), "t"));
            eq("<div a=\"9\" c=\"3\">t</div>", e.html());
        });

        test("text node updates in place", () -> {
            Env e = new Env();
            e.render(el("p", "one"));
            Node text = e.first().children.get(0);
            e.render(el("p", "two"));
            check(e.first().children.get(0) == text, "text node was replaced");
            eq("<p>two</p>", e.html());
        });

        test("fragment children are inserted before the right sibling", () -> {
            Env e = new Env();
            e.render(el("div", el("i", "1"), el("b", "3")));
            e.render(el("div", el("i", "1"), Jsx.fragment(null, el("u", "2a"), el("u", "2b")), el("b", "3")));
            eq("<div><i>1</i><u>2a</u><u>2b</u><b>3</b></div>", e.html());
        });

        test("useEffect: runs after commit, re-runs on dep change, cleans up on unmount", () -> {
            Env e = new Env();
            log.clear();
            e.render(Logger.make("L", "a"));
            eq("[mount L:a]", log.toString());
            e.render(Logger.make("L", "a"));
            eq("[mount L:a]", log.toString());      // same deps: nothing
            e.render(Logger.make("L", "b"));
            eq("[mount L:a, cleanup L:a, mount L:b]", log.toString());
            e.root.unmount(); e.flush();
            eq("[mount L:a, cleanup L:a, mount L:b, cleanup L:b]", log.toString());
            eq("", e.html());
        });

        test("effects: children first, cleanups before new effects", () -> {
            Env e = new Env();
            log.clear();
            e.render(el("div", Logger.make("A", "1"), Logger.make("B", "1")));
            eq("[mount A:1, mount B:1]", log.toString());
            log.clear();
            e.render(el("div", Logger.make("A", "2"), Logger.make("B", "2")));
            eq("[cleanup A:1, cleanup B:1, mount A:2, mount B:2]", log.toString());
        });

        test("effect sees the committed host tree and the ref", () -> {
            Env e = new Env();
            class Probe extends Component<Probe.Props> {
                record Props() {}
                public Element render(Props p) {
                    Ref<Object> ref = useRef(null);
                    useEffect(() -> log.add("ref=" + (ref.current != null)));
                    return Jsx.el("div", null, new Attrs().ref(ref));
                }
            }
            log.clear();
            e.render(c(Probe.class, Probe::new, null, new Probe.Props()));
            eq("[ref=true]", log.toString());
        });

        test("ref is cleared on unmount", () -> {
            Env e = new Env();
            Ref<Object> ref = new Ref<>(null);
            e.render(Jsx.el("div", null, new Attrs().ref(ref)));
            check(ref.current == e.first(), "ref not attached");
            e.root.unmount();
            check(ref.current == null, "ref not cleared");
        });

        test("useMemo recomputes only when deps change, useRef is stable", () -> {
            Env e = new Env();
            Memo.computes = 0; Memo.refs.clear();
            e.render(c(Memo.class, Memo::new, null, new Memo.Props(1, 1)));
            e.render(c(Memo.class, Memo::new, null, new Memo.Props(1, 2)));
            eq("<u>10/2</u>", e.html());
            eq(1, Memo.computes);
            e.render(c(Memo.class, Memo::new, null, new Memo.Props(2, 2)));
            eq("<u>20/2</u>", e.html());
            eq(2, Memo.computes);
            check(Memo.refs.get(0) == Memo.refs.get(1) && Memo.refs.get(1) == Memo.refs.get(2), "ref identity changed");
        });

        test("conditional hooks are detected", () -> {
            Env e = new Env();
            boolean[] extra = {false};
            class Bad extends Component<Bad.Props> {
                record Props() {}
                public Element render(Props p) {
                    useState(0);
                    if (extra[0]) useState(1);
                    return el("p", "x");
                }
            }
            e.render(c(Bad.class, Bad::new, null, new Bad.Props()));
            extra[0] = true;
            throwsMsg("more hooks", () -> e.render(c(Bad.class, Bad::new, null, new Bad.Props())));
        });

        test("setState after unmount is ignored", () -> {
            Env e = new Env();
            e.render(Counter.make("n"));
            State<Integer> s = Counter.last;
            e.root.unmount();
            e.flush();
            s.set(5);
            check(!e.clock.hasPending(), "work scheduled for an unmounted component");
        });

        test("runaway setState in an effect hits the update limit", () -> {
            Env e = new Env();
            class Loop extends Component<Loop.Props> {
                record Props() {}
                public Element render(Props p) {
                    State<Integer> n = useState(0);
                    useEffect(() -> n.update(x -> x + 1));
                    return el("p", n.get());
                }
            }
            throwsMsg("Maximum update depth", () -> e.render(c(Loop.class, Loop::new, null, new Loop.Props())));
        });

        test("setState during render is rejected", () -> {
            Env e = new Env();
            class Bad extends Component<Bad.Props> {
                record Props() {}
                public Element render(Props p) {
                    State<Integer> n = useState(0);
                    n.set(1);
                    return el("p", "x");
                }
            }
            throwsMsg("while rendering", () -> e.render(c(Bad.class, Bad::new, null, new Bad.Props())));
        });

        test("a render error leaves the committed tree intact", () -> {
            Env e = new Env();
            boolean[] fail = {false};
            class Maybe extends Component<Maybe.Props> {
                record Props() {}
                public Element render(Props p) {
                    if (fail[0]) throw new IllegalStateException("boom");
                    return el("p", "ok");
                }
            }
            e.render(c(Maybe.class, Maybe::new, null, new Maybe.Props()));
            fail[0] = true;
            throwsMsg("boom", () -> e.render(c(Maybe.class, Maybe::new, null, new Maybe.Props())));
            eq("<p>ok</p>", e.html());
            fail[0] = false;
            e.render(c(Maybe.class, Maybe::new, null, new Maybe.Props()));
            eq("<p>ok</p>", e.html());
        });

        test("renderSync commits immediately", () -> {
            Env e = new Env();
            e.root.renderSync(el("p", "now"));
            eq("<p>now</p>", e.html());
        });

        // ---------------------------------------------------------------- memo

        test("memo component skips a render when props are equal, plain components do not", () -> {
            Env e = new Env();
            e.render(TickParent.make());
            resetCounts();
            tickState.set(1); e.flush();
            eq("<div>1<b>a0</b><i>a</i></div>", e.html());
            eq(0, memoRenders);
            eq(1, plainRenders);
        });

        test("memo component re-renders when props change", () -> {
            Env e = new Env();
            e.render(MemoProbe.make("a"));
            resetCounts();
            e.render(MemoProbe.make("a"));
            eq(0, memoRenders);
            e.render(MemoProbe.make("b"));
            eq(1, memoRenders);
            eq("<b>b0</b>", e.html());
        });

        test("memo component still updates from its own state", () -> {
            Env e = new Env();
            e.render(TickParent.make());
            resetCounts();
            MemoProbe.own.set(5); e.flush();
            eq("<div>0<b>a5</b><i>a</i></div>", e.html());
            eq(1, memoRenders);
            eq(0, plainRenders);
        });

        test("custom arePropsEqual decides what counts as changed", () -> {
            Env e = new Env();
            e.render(CustomMemo.make("a", "1"));
            resetCounts();
            e.render(CustomMemo.make("a", "2"));        // b ignored
            eq(0, memoRenders);
            eq("<u>a1</u>", e.html());
            e.render(CustomMemo.make("x", "2"));
            eq(1, memoRenders);
            eq("<u>x2</u>", e.html());
        });

        // ---------------------------------------------------------------- context

        test("context: default value without a provider", () -> {
            Env e = new Env();
            e.render(Themed.make());
            eq("<p>light/0</p>", e.html());
        });

        test("context: provider value reaches nested consumers, a nested provider shadows it", () -> {
            Env e = new Env();
            e.render(THEME.provide("dark", el("div", Themed.make(), THEME.provide("blue", Themed.make()))));
            eq("<div><p>dark/0</p><p>blue/0</p></div>", e.html());
        });

        test("context: a changed value reaches a consumer below a memo component that did not render", () -> {
            Env e = new Env();
            e.render(THEME.provide("a", Mid.make("x")));
            eq("<div><p>a/0</p><s>1</s></div>", e.html());
            resetCounts();
            e.render(THEME.provide("b", Mid.make("x")));
            eq("<div><p>b/0</p><s>1</s></div>", e.html());
            eq(0, midRenders);
            eq(1, themedRenders);
            eq(0, sizedRenders);                        // reads another context: untouched
        });

        test("context: an equal value leaves the memoized subtree alone", () -> {
            Env e = new Env();
            e.render(THEME.provide("a", Mid.make("x")));
            resetCounts();
            e.render(THEME.provide("a", Mid.make("x")));
            eq(0, midRenders + themedRenders + sizedRenders);
        });

        test("context: contexts are independent", () -> {
            Env e = new Env();
            e.render(SIZE.provide(2, THEME.provide("a", Mid.make("x"))));
            eq("<div><p>a/0</p><s>2</s></div>", e.html());
            resetCounts();
            e.render(SIZE.provide(3, THEME.provide("a", Mid.make("x"))));
            eq("<div><p>a/0</p><s>3</s></div>", e.html());
            eq(1, sizedRenders);
            eq(0, themedRenders);
        });

        test("context: a consumer keeps its state when the value changes", () -> {
            Env e = new Env();
            e.render(THEME.provide("a", Mid.make("x")));
            themedState.set(5); e.flush();
            e.render(THEME.provide("b", Mid.make("x")));
            eq("<div><p>b/5</p><s>1</s></div>", e.html());
        });

        test("context: consumers also work with server rendering", () -> {
            eq("<p>light/0</p>", ServerRenderer.renderToString(Themed.make()));
            eq("<div><p>dark/0</p><p>blue/0</p><p>dark/0</p></div>", ServerRenderer.renderToString(
                    THEME.provide("dark", el("div", Themed.make(), THEME.provide("blue", Themed.make()), Themed.make()))));
        });

        // ---------------------------------------------------------------- dev warnings

        test("warns once about list items without a key, naming the component", () -> {
            Env e = new Env();
            class Rows extends Component<Rows.Props> {
                record Props() {}
                public Element render(Props p) {
                    return Jsx.el("ul", null, null, List.of(Jsx.el("li", null, null, "a"), Jsx.el("li", null, null, "b")));
                }
            }
            e.render(c(Rows.class, Rows::new, null, new Rows.Props()));
            e.render(c(Rows.class, Rows::new, null, new Rows.Props()));
            eq(1, e.warnings.size());
            check(e.warnings.get(0).contains("unique \"key\"") && e.warnings.get(0).contains("<li>"), e.warnings.get(0));
            check(e.warnings.get(0).contains("in Rows"), e.warnings.get(0));
        });

        test("no warning for keyed lists, fixed JSX children or plain text lists", () -> {
            Env e = new Env();
            class Card extends Component<Card.Props> {
                record Props(List<Object> children) {}
                public Element render(Props p) { return Jsx.el("div", null, null, p.children()); }
            }
            e.render(el("div", list("a", "b"),
                    c(Card.class, Card::new, null, new Card.Props(Jsx.children(el("i", "x"), el("b", "y")))),
                    List.of("t1", "t2")));
            eq("[]", e.warnings.toString());
        });

        test("warns about duplicate keys", () -> {
            Env e = new Env();
            e.render(list("a", "a"));
            eq(1, e.warnings.size());
            check(e.warnings.get(0).contains("same key `a`"), e.warnings.get(0));
        });

        test("dev mode can be switched off", () -> {
            Env e = new Env();
            e.reconciler.setDevMode(false);
            e.render(list("a", "a"));
            e.render(Jsx.el("ul", null, null, List.of(el("li", "x"))));
            eq("[]", e.warnings.toString());
        });

        // ---------------------------------------------------------------- scheduling and priorities

        test("normal updates render in a single task, never sliced", () -> {
            Env e = new Env();
            e.render(Page.make());
            eq(Page.html("p0"), e.html());
            Page.page.set(1);
            e.clock.step();                         // 30ms of work, far over the 5ms slice
            eq(Page.html("p1"), e.html());
        });

        test("a transition renders in slices and commits all at once", () -> {
            Env e = new Env();
            e.render(Page.make());
            e.reconciler.startTransition(() -> Page.page.set(1));
            int tasks = 0;
            while (e.clock.step()) {
                tasks++;
                String h = e.html();
                check(h.equals(Page.html("p0")) || h.equals(Page.html("p1")), "torn tree: " + h);
                if (tasks == 1) eq(Page.html("p0"), h);     // first slice only did part of the work
            }
            eq(Page.html("p1"), e.html());
            check(tasks > 2, "was not sliced: " + tasks + " task(s)");
        });

        test("an urgent update finishes a paused transition at once", () -> {
            Env e = new Env();
            e.render(Page.make());
            e.reconciler.startTransition(() -> Page.page.set(1));
            e.clock.step();
            eq(Page.html("p0"), e.html());          // paused
            e.reconciler.discreteUpdates(() -> Page.flag.set(true));
            e.clock.step();                         // urgent task: no more slicing, transition commits
            eq(Page.html("p1"), e.html());
            e.flush();                              // the urgent update gets its own render
            eq(Page.html("p1!"), e.html());
        });

        test("an overdue transition stops yielding", () -> {
            Env e = new Env();
            e.render(Page.make());
            e.reconciler.startTransition(() -> Page.page.set(1));
            e.clock.step();
            eq(Page.html("p0"), e.html());
            e.clock.advance(10_001);                // LOW timeout passed
            e.clock.step();
            eq(Page.html("p1"), e.html());
        });

        test("effects run in their own task, after the commit", () -> {
            Env e = new Env();
            log.clear();
            e.root.render(el("div", c(Slow.class, Slow::new, null, new Slow.Props("s", 6)), Logger.make("L", "a")));
            e.clock.step();                         // render + commit used up the slice
            eq("<div><i>s</i><i>L</i></div>", e.html());
            eq("[]", log.toString());
            e.clock.step();
            eq("[mount L:a]", log.toString());
        });

        test("pending effects run before the next render", () -> {
            Env e = new Env();
            log.clear();
            Supplier<Element> tree = () -> el("div", c(Slow.class, Slow::new, null, new Slow.Props("s", 6)), Logger.make("L", "a"));
            e.root.render(tree.get());
            e.clock.step();
            eq("[]", log.toString());
            e.root.render(el("div", c(Slow.class, Slow::new, null, new Slow.Props("s", 6)), Logger.make("L", "b")));
            e.flush();
            eq("[mount L:a, cleanup L:a, mount L:b]", log.toString());
        });

        test("renderSync runs effects immediately", () -> {
            Env e = new Env();
            log.clear();
            e.root.renderSync(Logger.make("L", "a"));
            eq("[mount L:a]", log.toString());
        });

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        if (!failures.isEmpty()) System.exit(1);
    }

    static Element list(String... keys) {
        List<Object> items = new ArrayList<>();
        for (String k : keys) items.add(Jsx.el("li", k, null, k));
        return Jsx.el("ul", null, null, items);
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

    static void throwsMsg(String part, Runnable r) {
        try { r.run(); } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains(part)) return;
            throw new AssertionError("wrong message: " + ex.getMessage());
        }
        throw new AssertionError("expected exception containing: " + part);
    }
}

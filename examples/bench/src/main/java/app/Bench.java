package app;

import jreact.Jsx;
import jreact.dom.DomRoot;
import jreact.web.JsNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The js-framework-benchmark scenarios. Every scenario renders a new row list with {@code renderSync} (data to
 * element tree to reconcile to DOM to forced layout) and is timed with performance.now(). Setup between runs is not timed.
 */
final class Bench {
    private static final int WARMUP = 3, RUNS = 10;

    private record Scenario(String name, int setupRows, UnaryOperator<List<Data.Item>> op) {}

    private final DomRoot<JsNode> root;
    private List<Data.Item> rows = List.of();

    Bench(DomRoot<JsNode> root) { this.root = root; }

    private void set(List<Data.Item> next) {
        rows = next;
        root.renderSync(Jsx.c(Table.class, Table::new, null, new Table.Props(next)));
    }

    String runAll() {
        List<Scenario> scenarios = List.of(
                new Scenario("create1k", 0, r -> Data.build(1000)),
                new Scenario("replace1k", 1000, r -> Data.build(1000)),
                new Scenario("update10th", 1000, r -> {
                    List<Data.Item> next = new ArrayList<>(r);
                    for (int i = 0; i < next.size(); i += 10) next.set(i, new Data.Item(next.get(i).id(), next.get(i).label() + " !!!"));
                    return next;
                }),
                new Scenario("swap", 1000, r -> { List<Data.Item> next = new ArrayList<>(r); Collections.swap(next, 1, 998); return next; }),
                new Scenario("remove", 1000, r -> { List<Data.Item> next = new ArrayList<>(r); next.remove(500); return next; }),
                new Scenario("append1k", 1000, r -> { List<Data.Item> next = new ArrayList<>(r); next.addAll(Data.build(1000)); return next; }),
                new Scenario("clear", 1000, r -> List.of()),
                new Scenario("create10k", 0, r -> Data.build(10000)));

        StringBuilder json = new StringBuilder("{");
        for (Scenario s : scenarios) {
            double[] times = new double[RUNS];
            for (int i = 0; i < WARMUP + RUNS; i++) {
                set(List.of());
                if (s.setupRows() > 0) set(Data.build(s.setupRows()));
                Native.layout();
                double start = Native.now();
                set(s.op().apply(rows));
                Native.layout();
                double elapsed = Native.now() - start;
                if (i >= WARMUP) times[i - WARMUP] = elapsed;
            }
            java.util.Arrays.sort(times);
            if (json.length() > 1) json.append(',');
            json.append('"').append(s.name()).append("\":{\"median\":").append(round(times[RUNS / 2]))
                    .append(",\"min\":").append(round(times[0])).append('}');
        }
        set(List.of());
        return json.append('}').toString();
    }

    private static double round(double ms) { return Math.round(ms * 10) / 10.0; }
}

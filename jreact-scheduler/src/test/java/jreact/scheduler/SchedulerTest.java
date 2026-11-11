package jreact.scheduler;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free tests of the scheduler on a virtual clock (run: ./build.sh test). */
public final class SchedulerTest {

    static ManualSchedulerHost host;
    static Scheduler scheduler;
    static List<String> log;

    static void setup() {
        host = new ManualSchedulerHost();
        scheduler = new Scheduler(host);
        log = new ArrayList<>();
    }

    public static void main(String[] args) {
        test("runs nothing until the host runs a macrotask", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, t -> { log.add("a"); return null; });
            eq("[]", log);
            host.runUntilIdle();
            eq("[a]", log);
        });

        test("more urgent priority runs first, same priority is FIFO", () -> {
            scheduler.scheduleCallback(Priority.LOW, t -> { log.add("low"); return null; });
            scheduler.scheduleCallback(Priority.NORMAL, t -> { log.add("n1"); return null; });
            scheduler.scheduleCallback(Priority.NORMAL, t -> { log.add("n2"); return null; });
            scheduler.scheduleCallback(Priority.IMMEDIATE, t -> { log.add("imm"); return null; });
            scheduler.scheduleCallback(Priority.USER_BLOCKING, t -> { log.add("ub"); return null; });
            host.runUntilIdle();
            eq("[imm, ub, n1, n2, low]", log);
        });

        test("yields after the frame interval and continues in the next macrotask", () -> {
            int[] chunk = {0};
            Scheduler.Callback[] work = new Scheduler.Callback[1];
            work[0] = t -> {
                log.add("c" + (++chunk[0]) + "@m" + (host.macrotasks()));
                host.advance(2);
                return chunk[0] < 5 ? work[0] : null;
            };
            scheduler.scheduleCallback(Priority.NORMAL, work[0]);
            host.runUntilIdle();
            // 2ms per chunk, 5ms slice: three chunks fit in the first macrotask (0, 2, 4ms < 5ms)
            eq("[c1@m1, c2@m1, c3@m1, c4@m2, c5@m2]", log);
        });

        test("shouldYield becomes true once the slice is used up", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, t -> {
                log.add("before=" + scheduler.shouldYield());
                host.advance(5);
                log.add("after=" + scheduler.shouldYield());
                return null;
            });
            host.runUntilIdle();
            eq("[before=false, after=true]", log);
        });

        test("an expired task is not postponed and sees didTimeout", () -> {
            scheduler.scheduleCallback(Priority.LOW, t -> { log.add("low timeout=" + t); return null; });
            scheduler.scheduleCallback(Priority.NORMAL, t -> { host.advance(20_000); log.add("slow"); return null; });
            // `slow` ran first (earlier expiration) and burned 20s; `low` expired and must run despite no time left
            host.runUntilIdle();
            eq("[slow, low timeout=true]", log);
        });

        test("old low-priority work is not starved by newer normal work", () -> {
            scheduler.scheduleCallback(Priority.LOW, t -> { log.add("low"); return null; });   // expires at 10000
            host.advance(6_000);
            scheduler.scheduleCallback(Priority.NORMAL, t -> { log.add("normal"); return null; }); // expires at 11000
            host.runUntilIdle();
            eq("[low, normal]", log);
        });

        test("a continuation keeps the task's priority", () -> {
            int[] n = {0};
            Scheduler.Callback[] work = new Scheduler.Callback[1];
            work[0] = t -> { log.add(scheduler.currentPriority().name()); host.advance(6); return ++n[0] < 2 ? work[0] : null; };
            scheduler.scheduleCallback(Priority.USER_BLOCKING, work[0]);
            host.runUntilIdle();
            eq("[USER_BLOCKING, USER_BLOCKING]", log);
        });

        test("cancelCallback skips the task", () -> {
            Task t = scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("never"); return null; });
            scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("kept"); return null; });
            scheduler.cancelCallback(t);
            host.runUntilIdle();
            eq("[kept]", log);
        });

        test("a task may cancel its own continuation by returning null", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("once"); return null; });
            host.runUntilIdle();
            check(!host.hasPending(), "still pending");
        });

        test("delayed task waits for its time", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("late"); return null; }, 100);
            host.runUntilIdle();
            eq("[]", log);
            host.advance(99); host.runUntilIdle();
            eq("[]", log);
            host.advance(1); host.runUntilIdle();
            eq("[late]", log);
        });

        test("delayed tasks run in start order, and runnable tasks go first", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("d200"); return null; }, 200);
            scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("d100"); return null; }, 100);
            scheduler.scheduleCallback(Priority.IDLE, d -> { log.add("now"); return null; });
            host.runUntilIdle();
            eq("[now]", log);
            host.advance(250); host.runUntilIdle();
            eq("[now, d100, d200]", log);
        });

        test("a task can schedule more work, which runs in the same slice", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, d -> {
                log.add("outer");
                scheduler.scheduleCallback(Priority.NORMAL, e -> { log.add("inner"); return null; });
                return null;
            });
            host.step();
            eq("[outer, inner]", log);
        });

        test("a throwing task does not stop the others", () -> {
            scheduler.scheduleCallback(Priority.NORMAL, d -> { throw new IllegalStateException("boom"); });
            scheduler.scheduleCallback(Priority.NORMAL, d -> { log.add("after"); return null; });
            try { host.step(); throw new AssertionError("expected exception"); }
            catch (IllegalStateException expected) { eq("boom", expected.getMessage()); }
            host.runUntilIdle();
            eq("[after]", log);
        });

        test("runWithPriority sets and restores the current priority", () -> {
            eq("NORMAL", scheduler.currentPriority());
            scheduler.runWithPriority(Priority.LOW, () -> eq("LOW", scheduler.currentPriority()));
            eq("NORMAL", scheduler.currentPriority());
        });

        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAIL " + f));
        if (!failures.isEmpty()) System.exit(1);
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    static void test(String name, Runnable body) {
        setup();
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

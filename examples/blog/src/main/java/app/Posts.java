package app;

import java.util.List;
import java.util.Locale;

/** The blog's content, in memory. */
public final class Posts {
    private Posts() {}

    public static final List<Post> ALL = List.of(
        new Post(1, "Fibers, explained without the jargon", "java", "Nov 02", 6,
            "A fiber is a unit of work you can pause. That one idea explains most of React.",
            "A fiber is a plain object that says what to render, where it sits in the tree and what to do next. Because the reconciler walks fibers instead of recursing, it can stop between two of them and let the browser breathe."),
        new Post(2, "Why keys matter in lists", "react", "Oct 28", 4,
            "Without keys the reconciler can only compare by position, and state follows the position.",
            "Delete the first row of an unkeyed list and every row below inherits the state of the one above it. A key ties state to identity instead of position."),
        new Post(3, "Compiling JSX to Java", "java", "Oct 21", 8,
            "jrxc turns angle brackets into method calls and keeps line numbers, so stack traces still point at your file.",
            "The compiler reads a record Props to know which attributes a component accepts, and javac checks the types afterwards. Mistakes show up as ordinary compile errors."),
        new Post(4, "Routing without a page reload", "react", "Oct 14", 5,
            "The History API lets a single page change its address. A router is the glue between that address and your components.",
            "pushState changes the URL without a request, popstate tells you when the user presses back. Everything else is matching a path against a list of patterns."),
        new Post(5, "TeaVM: Java in the browser", "java", "Oct 07", 7,
            "TeaVM compiles bytecode to JavaScript ahead of time, so there is no runtime to download.",
            "The result for a small app is about 120 KB. Reflection is limited, which is why jreact passes factories instead of looking constructors up by name."),
        new Post(6, "Tailwind and component libraries", "css", "Sep 30", 3,
            "Utility classes live next to the markup they style, which makes components easy to move.",
            "The one rule: write class names as whole strings, so the scanner can find them. Never build them with string concatenation."),
        new Post(7, "Error boundaries in practice", "react", "Sep 22", 5,
            "One broken widget should not blank the page. Wrap it and give it a way to recover.",
            "A boundary catches errors thrown while rendering its children and shows a fallback instead. Event handlers are outside its reach on purpose."),
        new Post(8, "Measuring before optimising", "java", "Sep 15", 6,
            "Our table is slower than React's. Here is how we know, and what we did not measure.",
            "Numbers from one machine and one table. Look at ratios, not absolute times, and remember what was left out: memoisation, other browsers, real apps.")
    );

    public static Post find(String id) {
        try {
            int n = Integer.parseInt(id);
            return ALL.stream().filter(p -> p.id() == n).findFirst().orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> tags() { return ALL.stream().map(Post::tag).distinct().sorted().toList(); }

    /** Posts with this tag (null or empty: all) whose title contains the query (case-insensitive). */
    public static List<Post> filter(String tag, String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        return ALL.stream()
            .filter(p -> tag == null || tag.isEmpty() || p.tag().equals(tag))
            .filter(p -> q.isEmpty() || p.title().toLowerCase(Locale.ROOT).contains(q))
            .toList();
    }
}

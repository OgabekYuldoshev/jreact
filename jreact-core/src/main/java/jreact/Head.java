package jreact;

import java.util.ArrayList;
import java.util.List;

/**
 * What a page puts into {@code <head>}: the title and {@code <meta>}/{@code <link>} tags. Immutable; use it with
 * {@link Component#useHead(Head)} (in a page or layout) or {@code @Page(title = "...")}:
 * <pre>{@code
 * useHead(Head.title(book.title() + " · Library")
 *     .meta("description", book.author())
 *     .property("og:title", book.title())          // <meta property="og:title" ...>
 *     .link("canonical", "https://example.com/books/" + book.id()));
 * }</pre>
 * On the server the tags are written into the page's {@code <head>} (search engines and link previews see them); in the browser they
 * follow navigation. The deepest component wins: a page's title replaces its layout's, and a tag with the same key (the same
 * {@code name}, {@code property} or {@code rel}) is replaced, not repeated.
 */
public record Head(String title, List<Tag> tags) {

    /**
     * One tag. {@code element} is "meta" or "link"; the tag is identified by {@code keyAttr=keyValue} ({@code name=description}) and
     * carries {@code valueAttr=value} ({@code content=...}).
     */
    public record Tag(String element, String keyAttr, String keyValue, String valueAttr, String value) {
        public String key() { return element + "|" + keyAttr + "|" + keyValue; }
    }

    public Head {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static final Head EMPTY = new Head(null, List.of());

    public static Head title(String title) { return new Head(title, List.of()); }

    /** {@code <meta name="..." content="...">} */
    public Head meta(String name, String content) { return with(new Tag("meta", "name", name, "content", content)); }

    /** {@code <meta property="..." content="...">} (Open Graph: {@code og:title}, {@code og:image}...) */
    public Head property(String property, String content) { return with(new Tag("meta", "property", property, "content", content)); }

    /** {@code <link rel="..." href="...">} (canonical, icon...) */
    public Head link(String rel, String href) { return with(new Tag("link", "rel", rel, "href", href)); }

    public Head withTitle(String title) { return new Head(title, tags); }

    private Head with(Tag tag) {
        List<Tag> next = new ArrayList<>(tags);
        next.removeIf(t -> t.key().equals(tag.key()));
        next.add(tag);
        return new Head(title, next);
    }

    /** {@code other} on top of this: its title wins when it has one, its tags replace tags with the same key. */
    public Head merge(Head other) {
        if (other == null) return this;
        List<Tag> next = new ArrayList<>(tags);
        for (Tag t : other.tags) {
            next.removeIf(old -> old.key().equals(t.key()));
            next.add(t);
        }
        return new Head(other.title != null ? other.title : title, next);
    }

    /** The tags as HTML for the end of {@code <head>} (the title is separate: see {@link #titleHtml()}). Values are escaped; the {@code data-jreact-head} mark lets the browser take the tags over. */
    public String tagsHtml() {
        StringBuilder b = new StringBuilder();
        for (Tag t : tags) {
            b.append('<').append(t.element()).append(' ').append(t.keyAttr()).append("=\"").append(escape(t.keyValue())).append("\" ")
                    .append(t.valueAttr()).append("=\"").append(escape(t.value())).append("\" data-jreact-head>");
        }
        return b.toString();
    }

    /** {@code <title>...</title>}, or "" without a title. */
    public String titleHtml() { return title == null ? "" : "<title>" + escape(title) + "</title>"; }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

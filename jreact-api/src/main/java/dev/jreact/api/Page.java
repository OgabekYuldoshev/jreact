package dev.jreact.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A page of the app, at a URL path: the browser's {@code @GetMapping}. Put it on a component, and the routes are generated, there is no routes file to keep up to date:
 * <pre>{@code
 * @Page("/books/:id")                      // or "/books/{id}", like Spring
 * public class BookDetail extends Component<BookDetail.Props> {
 *     public record Props() {}            // a page has no props: its data comes from the loader
 *
 *     public static Object loader(DataArgs a) { return BookApiClient.get(Long.parseLong(a.params().get("id"))); }   // optional
 *     public static Object action(DataArgs a) { ... }                                                              // optional: form posts
 *
 *     public Element render(Props props) { Book b = (Book) useLoaderData(); ... }
 * }
 * }</pre>
 * <ul>
 *   <li>{@code "/"} is the home page, {@code "/books/:id"} has a parameter ({@code a.params().get("id")}), {@code "/docs/*"} matches the rest of the URL.</li>
 *   <li>A page is nested in the {@link Layout} whose path is the longest prefix of its path. A page at the same path as a layout is that layout's index page.</li>
 *   <li>{@code error}: shown instead of the page when its loader or action fails (a component with an empty {@code Props}).</li>
 *   <li>Two optional {@code public static} methods, found by name: {@code loader(DataArgs)} and {@code action(DataArgs)}; they may return a {@code Promise}.</li>
 * </ul>
 * The annotation processor in {@code jreact-processor} generates {@code dev.jreact.generated.Pages.routes()} and a ready {@code dev.jreact.generated.PagesApp}
 * ({@link ClientApp}); use {@code new PagesApp()} in {@code WebMain} and set {@code jreact.ssr.app=dev.jreact.generated.PagesApp} for server side rendering.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Page {
    /** The URL path, from the root of the app (the servlet context path is added by the router). */
    String value();

    /** Component shown when this page's loader or action fails; {@code Void.class} (default): the closest layout's error page, else the built in one. */
    Class<?> error() default Void.class;
}

package dev.jreact.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wraps every {@link Page} (and layout) under its path: a header, a sidebar, a login check. It renders {@code <Outlet />} where the page goes:
 * <pre>{@code
 * @Layout("/")                                   // around the whole app
 * public class AppLayout extends Component<AppLayout.Props> {
 *     public record Props() {}
 *     public Element render(Props props) { return <div><Header /><main><Outlet /></main></div>; }
 * }
 *
 * @Layout("/admin")                               // around /admin and everything below it, inside the root layout
 * public class AdminLayout ...
 * }</pre>
 * A layout may have {@code public static Object loader(DataArgs)} / {@code action(DataArgs)} like a page (for example a loader that fetches the signed in user and redirects when there is none).
 * Layout paths are unique: one layout per path.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Layout {
    /** The URL path this layout covers; {@code "/"} (default) is the whole app. */
    String value() default "/";

    /**
     * Component shown when a page below fails and has no error page of its own (a component with an empty {@code Props}); {@code Void.class}: the built in one.
     * It is shown <em>inside</em> this layout, in place of the page: the header and the rest of the layout stay.
     */
    Class<?> error() default Void.class;
}

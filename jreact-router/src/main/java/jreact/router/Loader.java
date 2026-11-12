package jreact.router;

/**
 * Fetches the data of a route <em>before</em> it renders; the page reads it with {@code useLoaderData()}.
 * Return the data, return a {@link Promise} to make the router wait, throw (or return {@link Redirect#to}) to redirect
 * or to show the route's {@code errorElement}.
 * <pre>{@code
 * <Route path="contacts/:id" loader={a -> Contacts.get(a.params().get("id"))} ... />
 * }</pre>
 */
@FunctionalInterface
public interface Loader {
    Object load(DataArgs args) throws Exception;
}

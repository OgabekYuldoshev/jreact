package jreact.router;

/**
 * Handles a form submission ({@code <Form method="post">}, a fetcher): do the change, return something for
 * {@code useActionData()} or {@link Redirect#to} where to go next. When it succeeds the loaders run again, so the page shows the new data.
 * Same shape as a {@link Loader}: it may return a {@link Promise} or throw.
 */
@FunctionalInterface
public interface Action {
    Object run(DataArgs args) throws Exception;
}

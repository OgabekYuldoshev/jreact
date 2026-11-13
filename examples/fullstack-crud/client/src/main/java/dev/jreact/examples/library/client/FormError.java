package dev.jreact.examples.library.client;

import java.util.Map;

/**
 * What a form action returns when the server refused the input: the page shows the message and stays where it is ({@code useActionData()}).
 * {@code fields} has the server's message per field (Bean Validation), shown under that field.
 */
public record FormError(String message, Map<String, String> fields) {
    public FormError(String message) { this(message, Map.of()); }

    /** The message for one field, or null. */
    public static String of(Object result, String field) {
        return result instanceof FormError e ? e.fields().get(field) : null;
    }
}

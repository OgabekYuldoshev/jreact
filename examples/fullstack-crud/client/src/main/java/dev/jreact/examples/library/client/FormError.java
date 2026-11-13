package dev.jreact.examples.library.client;

/** What a form action returns when the server refused the input: the page shows the message and stays where it is ({@code useActionData()}). */
public record FormError(String message) {}

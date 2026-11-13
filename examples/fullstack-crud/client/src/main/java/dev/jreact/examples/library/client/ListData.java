package dev.jreact.examples.library.client;

import dev.jreact.examples.library.shared.BookPage;

import java.util.Map;

/** What the list route's loader hands to the page: the books of the page and the count per status. */
public record ListData(BookPage page, Map<String, Long> counts) {}

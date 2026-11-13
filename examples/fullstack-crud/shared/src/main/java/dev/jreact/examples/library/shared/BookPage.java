package dev.jreact.examples.library.shared;

import java.util.List;

/** One page of a search result. {@code page} counts from 1. */
public record BookPage(List<Book> items, int page, int pages, long total) {}

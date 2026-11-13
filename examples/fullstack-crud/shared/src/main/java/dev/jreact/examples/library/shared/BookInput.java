package dev.jreact.examples.library.shared;

import java.util.Optional;

/** What the create and edit forms send. The server validates it (see BookService). */
public record BookInput(String title, String author, int year, BookStatus status, Optional<String> isbn) {}

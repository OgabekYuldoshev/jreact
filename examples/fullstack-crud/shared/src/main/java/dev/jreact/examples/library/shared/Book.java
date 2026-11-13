package dev.jreact.examples.library.shared;

import java.time.LocalDate;
import java.util.Optional;

/** A book as the UI sees it. Records like this one cross {@link BookApi} as JSON: both sides use this class. */
public record Book(long id, String title, String author, int year, BookStatus status, Optional<String> isbn, LocalDate addedOn) {}

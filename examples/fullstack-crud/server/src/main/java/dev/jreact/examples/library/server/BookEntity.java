package dev.jreact.examples.library.server;

import dev.jreact.examples.library.shared.BookStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** The database row. The UI never sees it: BookService maps it to the shared {@code Book} record. */
@Entity
@Table(name = "books")
class BookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 200)
    String title;

    @Column(nullable = false, length = 120)
    String author;

    @Column(name = "published_year", nullable = false)
    int year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    BookStatus status;

    @Column(length = 20)
    String isbn;

    @Column(nullable = false)
    LocalDate addedOn;

    protected BookEntity() {}

    BookEntity(String title, String author, int year, BookStatus status, String isbn, LocalDate addedOn) {
        this.title = title;
        this.author = author;
        this.year = year;
        this.status = status;
        this.isbn = isbn;
        this.addedOn = addedOn;
    }
}

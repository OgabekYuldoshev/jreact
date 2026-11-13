package dev.jreact.examples.library.shared;

/** Where a book is. An enum crosses {@link BookApi} as its name ("AVAILABLE"). */
public enum BookStatus {
    AVAILABLE("Available"), BORROWED("Borrowed"), LOST("Lost");

    private final String label;

    BookStatus(String label) { this.label = label; }

    public String label() { return label; }
}

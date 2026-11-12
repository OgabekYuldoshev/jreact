package app;

/** One contact. */
public record Contact(int id, String first, String last, String notes, boolean favorite) {

    public String fullName() {
        String n = (first + " " + last).trim();
        return n.isEmpty() ? "No name" : n;
    }

    public Contact with(String first, String last, String notes) { return new Contact(id, first, last, notes, favorite); }

    public Contact withFavorite(boolean f) { return new Contact(id, first, last, notes, f); }
}

package {{package}}.shared;

/** A todo. Records like this one (also lists, maps, enums, primitives, String) can cross {@link TodoApi}: both sides use this class. */
public record Todo(long id, String title, boolean done) {}

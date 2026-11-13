package {{package}}.shared;

import java.time.LocalDate;

/**
 * A todo. Records like this one (also lists, maps, enums, Optional, LocalDate/LocalDateTime/LocalTime/Instant, primitives, String)
 * can cross {@link TodoApi}: both sides use this class. Dates travel as ISO-8601 strings.
 */
public record Todo(long id, String title, boolean done, LocalDate created) {}

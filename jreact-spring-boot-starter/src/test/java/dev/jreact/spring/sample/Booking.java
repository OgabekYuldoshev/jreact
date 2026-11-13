package dev.jreact.spring.sample;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public record Booking(String who, LocalDate day, Optional<String> note, LocalDateTime start, Instant at) {}

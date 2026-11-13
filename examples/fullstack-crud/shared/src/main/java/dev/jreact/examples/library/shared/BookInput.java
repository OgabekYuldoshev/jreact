package dev.jreact.examples.library.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Optional;

/**
 * What the create and edit forms send. The rules are Bean Validation annotations: the server checks them before BookService runs
 * (it is {@code @Validated}) and a violation reaches the form as a 422 with one message per field.
 */
public record BookInput(
        @NotBlank(message = "The title must be 1 to 200 characters.") @Size(max = 200, message = "The title must be 1 to 200 characters.") String title,
        @NotBlank(message = "The author must be 1 to 120 characters.") @Size(max = 120, message = "The author must be 1 to 120 characters.") String author,
        @Min(value = 1000, message = "The year must be between 1000 and 9999.") @Max(value = 9999, message = "The year must be between 1000 and 9999.") int year,
        @NotNull(message = "Choose a status.") BookStatus status,
        Optional<String> isbn) {}

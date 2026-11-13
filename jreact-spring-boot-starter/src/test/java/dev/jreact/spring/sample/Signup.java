package dev.jreact.spring.sample;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Signup(@NotBlank @Size(max = 10) String name, @Min(18) int age) {}

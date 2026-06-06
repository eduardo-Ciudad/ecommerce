package com.eduardo.ecomerce.dto.input.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductInput(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 150) String name,
        String description
) {
}

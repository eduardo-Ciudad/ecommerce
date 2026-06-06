package com.eduardo.ecomerce.dto.input.cartitem;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartItemInput(
        @NotNull UUID variantId,
        @NotNull @Min(1) Integer quantity
) {
}

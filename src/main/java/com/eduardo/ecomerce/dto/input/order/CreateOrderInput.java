package com.eduardo.ecomerce.dto.input.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderInput(
        @NotNull UUID addressId,
        @NotBlank String shippingMethod
) {
}

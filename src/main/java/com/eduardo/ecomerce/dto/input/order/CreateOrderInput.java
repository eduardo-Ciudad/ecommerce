package com.eduardo.ecomerce.dto.input.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrderInput(
        @NotNull UUID addressId,
        @NotBlank String shippingMethod,
        @Size(max = 50, message = "Código do cupom deve ter no máximo 50 caracteres")
        String couponCode
) {
    public CreateOrderInput(UUID addressId, String shippingMethod) {
        this(addressId, shippingMethod, null);
    }
}

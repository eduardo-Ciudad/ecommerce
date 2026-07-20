package com.eduardo.ecomerce.dto.input.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentInput(
        @NotNull UUID orderId,
        @NotBlank String paymentMethod,
        String token,
        Integer installments,
        String cardIssuerId
) {
}

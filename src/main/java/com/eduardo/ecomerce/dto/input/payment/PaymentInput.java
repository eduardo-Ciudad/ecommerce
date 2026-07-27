package com.eduardo.ecomerce.dto.input.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record PaymentInput(
        @NotNull UUID orderId,


        @NotBlank
        @Pattern(regexp = "^(credit_card|pix)$", message = "Método de pagamento deve ser 'credit_card' ou 'pix'")
        String paymentMethod,
        String token,
        Integer installments,
        String cardIssuerId
) {
}

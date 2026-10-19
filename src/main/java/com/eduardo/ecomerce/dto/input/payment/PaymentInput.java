package com.eduardo.ecomerce.dto.input.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@ValidPayerDocument
public record PaymentInput(
        @NotNull UUID orderId,

        @NotBlank
        @Pattern(regexp = "^(credit_card|pix)$", message = "Método de pagamento deve ser 'credit_card' ou 'pix'")
        String paymentMethod,

        String token,

        Integer installments,

        String cardIssuerId,

        @Size(max = 32)
        String issuerId,

        @NotBlank(message = "Tipo de documento é obrigatório")
        @Pattern(regexp = "^(CPF|CNPJ)$", message = "Tipo de documento deve ser CPF ou CNPJ")
        String payerDocType,

        @NotBlank(message = "CPF/CNPJ é obrigatório")
        @Size(max = 18)
        String payerDocNumber,

        @Size(max = 255)
        String deviceId
) {
}

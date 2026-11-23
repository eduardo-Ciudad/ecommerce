package com.eduardo.ecomerce.dto.input.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Dados coletados no site antes de redirecionar ao checkout da InfinitePay (CPF e celular vão para o Bling). */
@ValidPayerDocument
public record InfinitePayCheckoutInput(
        @NotNull UUID orderId,

        @NotBlank(message = "Tipo de documento é obrigatório")
        @Pattern(regexp = "^(CPF|CNPJ)$", message = "Tipo de documento deve ser CPF ou CNPJ")
        String payerDocType,

        @NotBlank(message = "CPF/CNPJ é obrigatório")
        @Size(max = 18)
        String payerDocNumber,

        @NotBlank(message = "Celular é obrigatório")
        @Pattern(regexp = "^\\(?\\d{2}\\)?[\\s-]?\\d{4,5}-?\\d{4}$", message = "Celular inválido")
        String payerPhone
) implements PayerDocumentHolder {
}

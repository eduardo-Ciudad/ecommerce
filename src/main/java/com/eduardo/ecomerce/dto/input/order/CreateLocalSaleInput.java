package com.eduardo.ecomerce.dto.input.order;

import com.eduardo.ecomerce.domain.order.LocalPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Venda de balcão registrada pelo admin: sem carrinho, frete nem endereço; nome e celular viram o contato no Bling. */
public record CreateLocalSaleInput(
        @NotEmpty(message = "Informe ao menos um item")
        List<@Valid @NotNull Item> items,

        @NotNull(message = "Informe a forma de pagamento")
        LocalPaymentMethod paymentMethod,

        @NotBlank(message = "Informe o nome do cliente")
        @Size(max = 100, message = "Nome do cliente deve ter no máximo 100 caracteres")
        String customerName,

        @NotBlank(message = "Celular é obrigatório")
        @Pattern(regexp = "^\\(?\\d{2}\\)?[\\s-]?\\d{4,5}-?\\d{4}$", message = "Celular inválido")
        String customerPhone,

        @Size(max = 18, message = "CPF/CNPJ deve ter no máximo 18 caracteres")
        String customerDocument,

        @DecimalMin(value = "0.00", message = "Desconto não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "Desconto inválido")
        BigDecimal discountAmount
) {
    public record Item(
            @NotNull(message = "Informe a variante")
            UUID variantId,

            @NotNull(message = "Informe a quantidade")
            @Min(value = 1, message = "Quantidade deve ser no mínimo 1")
            Integer quantity
    ) {
    }
}

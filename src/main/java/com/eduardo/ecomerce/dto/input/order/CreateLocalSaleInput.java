package com.eduardo.ecomerce.dto.input.order;

import com.eduardo.ecomerce.domain.order.LocalPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Venda de balcão registrada pelo admin: sem carrinho, frete, endereço nem documento. */
public record CreateLocalSaleInput(
        @NotEmpty(message = "Informe ao menos um item")
        List<@Valid @NotNull Item> items,

        @NotNull(message = "Informe a forma de pagamento")
        LocalPaymentMethod paymentMethod,

        @Size(max = 100, message = "Nome do cliente deve ter no máximo 100 caracteres")
        String customerName,

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

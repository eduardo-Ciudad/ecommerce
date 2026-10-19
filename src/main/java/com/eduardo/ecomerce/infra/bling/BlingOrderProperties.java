package com.eduardo.ecomerce.infra.bling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuração do envio de pedidos do site para o Bling (bling.orders.*).
 * Os ids (loja, situação, formas de pagamento) vêm da conta Bling da cliente.
 */
@ConfigurationProperties(prefix = "bling.orders")
public record BlingOrderProperties(
        @DefaultValue("false") boolean enabled,
        Long storeId,
        Long situationId,
        PaymentMethods paymentMethod,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration staleAfter
) {

    public record PaymentMethods(Long pix, Long creditCard) {
    }

    /** Id da forma de pagamento no Bling para o meio usado no site ("pix" | "credit_card"). */
    public Long paymentMethodIdFor(String paymentMethod) {
        if (paymentMethod == null || this.paymentMethod == null) {
            throw new IllegalArgumentException("Meio de pagamento sem forma de pagamento no Bling: " + paymentMethod);
        }
        return switch (paymentMethod) {
            case "pix" -> this.paymentMethod.pix();
            case "credit_card" -> this.paymentMethod.creditCard();
            default -> throw new IllegalArgumentException(
                    "Meio de pagamento sem forma de pagamento no Bling: " + paymentMethod);
        };
    }
}

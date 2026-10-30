package com.eduardo.ecomerce.infra.bling;

import com.eduardo.ecomerce.domain.order.LocalPaymentMethod;
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
        @DefaultValue("15m") Duration staleAfter,
        Local local
) {

    public record PaymentMethods(Long pix, Long creditCard) {
    }

    /**
     * Venda local (bling.orders.local.*): contato genérico único ("Consumidor Balcão"),
     * situação do pedido (Atendido) e uma forma de pagamento do Bling para cada meio do balcão.
     */
    public record Local(Long contactId, Long situationId, LocalPaymentMethods paymentMethod) {
    }

    public record LocalPaymentMethods(Long cash, Long localPix, Long cardMachine, Long paymentLink, Long storeCredit) {
    }

    /** Id da forma de pagamento no Bling para o meio gravado no pedido (site ou balcão). */
    public Long paymentMethodIdFor(String paymentMethod) {
        Long id = paymentMethod == null ? null : switch (paymentMethod) {
            case "pix" -> this.paymentMethod == null ? null : this.paymentMethod.pix();
            case "credit_card" -> this.paymentMethod == null ? null : this.paymentMethod.creditCard();
            default -> localPaymentMethodIdFor(paymentMethod);
        };
        if (id == null) {
            throw new IllegalArgumentException("Meio de pagamento sem forma de pagamento no Bling: " + paymentMethod);
        }
        return id;
    }

    /** Id do contato genérico das vendas locais; falha com mensagem clara se não estiver configurado. */
    public Long localContactId() {
        if (local == null || local.contactId() == null) {
            throw new IllegalStateException("bling.orders.local.contact-id não configurado");
        }
        return local.contactId();
    }

    /** Situação das vendas locais no Bling; falha com mensagem clara se não estiver configurada. */
    public Long localSituationId() {
        if (local == null || local.situationId() == null) {
            throw new IllegalStateException("bling.orders.local.situation-id não configurado");
        }
        return local.situationId();
    }

    private Long localPaymentMethodIdFor(String code) {
        LocalPaymentMethods methods = local == null ? null : local.paymentMethod();
        if (methods == null) {
            return null;
        }
        for (LocalPaymentMethod method : LocalPaymentMethod.values()) {
            if (method.code().equals(code)) {
                return switch (method) {
                    case CASH -> methods.cash();
                    case PIX -> methods.localPix();
                    case CARD_MACHINE -> methods.cardMachine();
                    case PAYMENT_LINK -> methods.paymentLink();
                    case STORE_CREDIT -> methods.storeCredit();
                };
            }
        }
        return null;
    }
}

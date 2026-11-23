package com.eduardo.ecomerce.dto.input.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Corpo do webhook da InfinitePay (sem assinatura). Só os identificadores são usados:
 * valor, parcelas e meio de pagamento vêm sempre do payment_check.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InfinitePayWebhookPayload(
        String invoiceSlug,
        String orderNsu,
        String transactionNsu,
        Long amount,
        Long paidAmount,
        Integer installments,
        String captureMethod
) {
}

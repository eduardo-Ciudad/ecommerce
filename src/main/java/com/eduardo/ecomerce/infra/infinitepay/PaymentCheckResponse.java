package com.eduardo.ecomerce.infra.infinitepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** success=false ou paid=false chegam com HTTP 200: quem decide o que fazer é o serviço. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentCheckResponse(
        boolean success,
        boolean paid,
        Long amount,
        Long paidAmount,
        Integer installments,
        String captureMethod
) {
}

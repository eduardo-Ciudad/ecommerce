package com.eduardo.ecomerce.service.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        UUID orderId,
        BigDecimal amount,
        String description,
        String payerEmail
) {}

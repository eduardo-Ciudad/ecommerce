package com.eduardo.ecomerce.dto.output.payment;

import java.math.BigDecimal;

public record PaymentOutput(
        String id,
        String status,
        String statusDetail,
        String paymentMethod,
        BigDecimal total,
        String pixQrCode,
        String pixQrCodeBase64
) {
}

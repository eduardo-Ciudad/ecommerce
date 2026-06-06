package com.eduardo.ecomerce.dto.output.orderitem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemOutput(
        UUID id,
        UUID variantId,
        String productName,
        String size,
        Integer quantity,
        BigDecimal unitPrice
) {
}

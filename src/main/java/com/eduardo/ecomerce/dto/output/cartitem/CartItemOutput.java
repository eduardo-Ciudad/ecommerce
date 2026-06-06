package com.eduardo.ecomerce.dto.output.cartitem;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemOutput(
        UUID id,
        UUID variantId,
        String productName,
        String size,
        BigDecimal price,
        Integer quantity
) {
}

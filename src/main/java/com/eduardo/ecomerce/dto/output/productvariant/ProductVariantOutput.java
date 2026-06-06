package com.eduardo.ecomerce.dto.output.productvariant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductVariantOutput(
        UUID id,
        String size,
        BigDecimal price,
        Integer stock,
        LocalDateTime createdAt
) {
}

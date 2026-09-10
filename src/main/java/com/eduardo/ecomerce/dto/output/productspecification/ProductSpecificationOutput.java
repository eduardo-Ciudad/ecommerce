package com.eduardo.ecomerce.dto.output.productspecification;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductSpecificationOutput(
        UUID id,
        String name,
        String value,
        Integer displayOrder,
        LocalDateTime createdAt
) {
}

package com.eduardo.ecomerce.dto.output.productimage;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductImageOutput(
        UUID id,
        String url,
        String thumbnailUrl,
        Integer displayOrder,
        LocalDateTime createdAt
) {
}

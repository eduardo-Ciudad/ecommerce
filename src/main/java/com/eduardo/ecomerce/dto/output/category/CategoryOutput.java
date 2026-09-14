package com.eduardo.ecomerce.dto.output.category;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryOutput(
        UUID id,
        String name,
        String imageUrl,
        UUID parentId,
        Long blingCategoryId,
        LocalDateTime createdAt
) {
}

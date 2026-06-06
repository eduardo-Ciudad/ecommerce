package com.eduardo.ecomerce.dto.output.category;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryOutput(
        UUID id,
        String name,
        LocalDateTime createdAt
) {
}

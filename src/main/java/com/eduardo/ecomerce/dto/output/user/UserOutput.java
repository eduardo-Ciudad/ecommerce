package com.eduardo.ecomerce.dto.output.user;

import com.eduardo.ecomerce.domain.user.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserOutput(
        UUID id,
        String name,
        String email,
        UserRole role,
        LocalDateTime createdAt
) {
}

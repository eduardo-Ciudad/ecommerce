package com.eduardo.ecomerce.dto.input.token;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenInput(
        @NotBlank(message = "Refresh token é obrigatório")
        String refreshToken
) {}
package com.eduardo.ecomerce.dto.output.auth;

public record AuthOutput(
        String accessToken,
        String refreshToken
) {
}

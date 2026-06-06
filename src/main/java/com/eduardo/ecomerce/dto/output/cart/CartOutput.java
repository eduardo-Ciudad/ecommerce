package com.eduardo.ecomerce.dto.output.cart;

import com.eduardo.ecomerce.dto.output.cartitem.CartItemOutput;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CartOutput(
        UUID id,
        UUID userId,
        List<CartItemOutput> items,
        LocalDateTime createdAt
) {
}

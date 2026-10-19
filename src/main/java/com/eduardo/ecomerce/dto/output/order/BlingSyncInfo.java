package com.eduardo.ecomerce.dto.output.order;

import com.eduardo.ecomerce.domain.order.BlingSyncStatus;

import java.time.LocalDateTime;

/** Situação do envio do pedido ao Bling. Só é preenchido nas rotas de admin. */
public record BlingSyncInfo(
        BlingSyncStatus status,
        Long blingOrderId,
        int attempts,
        String error,
        LocalDateTime syncedAt
) {
}

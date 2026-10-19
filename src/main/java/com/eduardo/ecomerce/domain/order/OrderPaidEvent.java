package com.eduardo.ecomerce.domain.order;

import java.util.UUID;

/** Publicado quando um pedido passa para PAID. Consumido após o commit (envio ao Bling). */
public record OrderPaidEvent(UUID orderId) {
}

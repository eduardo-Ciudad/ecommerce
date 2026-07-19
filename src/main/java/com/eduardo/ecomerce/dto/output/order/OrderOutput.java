package com.eduardo.ecomerce.dto.output.order;

import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.dto.output.orderitem.OrderItemOutput;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderOutput(
        UUID id,
        UUID userId,
        BigDecimal total,
        OrderStatus status,
        String paymentStatus,
        String checkoutUrl,
        List<OrderItemOutput> items,
        LocalDateTime createdAt
) { }
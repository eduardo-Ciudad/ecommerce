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
        String shippingMethod,
        BigDecimal shippingPrice,
        Integer shippingDeadlineDays,
        String recipientName,
        String recipientCep,
        String recipientStreet,
        String recipientNumber,
        String recipientComplement,
        String recipientNeighborhood,
        String recipientCity,
        String recipientState,
        List<OrderItemOutput> items,
        LocalDateTime createdAt
) { }
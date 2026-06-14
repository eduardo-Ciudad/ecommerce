package com.eduardo.ecomerce.domain.order;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private Set<OrderStatus> allowedStatuses;

    static  {
        PENDING.allowedStatuses = Set.of(PAID, CANCELLED);
        PAID.allowedStatuses = Set.of(SHIPPED, CANCELLED);
        SHIPPED.allowedStatuses = Set.of(DELIVERED);
        DELIVERED.allowedStatuses = Set.of();
        CANCELLED.allowedStatuses = Set.of();
    }

    public boolean canTransition(OrderStatus target) {
        return allowedStatuses.contains(target);
    }
}

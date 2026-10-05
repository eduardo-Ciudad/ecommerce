package com.eduardo.ecomerce.dto.output.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponOutput(
        UUID id,
        String code,
        BigDecimal discountPercent,
        Boolean active,
        LocalDateTime createdAt
) {
}
package com.eduardo.ecomerce.dto.output.coupon;

import java.math.BigDecimal;

public record CouponValidationOutput(
        String code,
        BigDecimal discountPercent
) {
}

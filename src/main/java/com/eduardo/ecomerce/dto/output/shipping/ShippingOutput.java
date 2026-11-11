package com.eduardo.ecomerce.dto.output.shipping;


import java.math.BigDecimal;

public record ShippingOutput(
        String method,
        String methodLabel,
        BigDecimal price,
        Integer deadlineDays,
        boolean freeShipping
) {
    public ShippingOutput(String method, String methodLabel, BigDecimal price, Integer deadlineDays) {
        this(method, methodLabel, price, deadlineDays, false);
    }
}

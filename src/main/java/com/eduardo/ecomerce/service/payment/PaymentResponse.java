package com.eduardo.ecomerce.service.payment;


public record PaymentResponse(
        String checkoutUrl,
        String gatewayReference
) {}

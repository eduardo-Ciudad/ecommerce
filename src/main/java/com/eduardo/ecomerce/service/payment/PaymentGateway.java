package com.eduardo.ecomerce.service.payment;

public interface PaymentGateway {

    PaymentResponse createCheckout(PaymentRequest request);

    PaymentStatus getPaymentStatus(String gatewayReference);
}
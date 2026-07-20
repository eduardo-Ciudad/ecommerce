package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;

    public PaymentOutput processPayment(PaymentInput input, String payerEmail) {
        Order order = orderRepository.findById(input.orderId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        PaymentCreateRequest.PaymentCreateRequestBuilder requestBuilder = PaymentCreateRequest.builder()
                .transactionAmount(order.getTotal())
                .description("MiniModa - Pedido #" + order.getId().toString().substring(0, 8))
                .externalReference(order.getId().toString())
                .payer(PaymentPayerRequest.builder()
                        .email(payerEmail)
                        .build());

        if ("credit_card".equals(input.paymentMethod())) {
            requestBuilder
                    .token(input.token())
                    .installments(input.installments() != null ? input.installments() : 1)
                    .paymentMethodId(input.cardIssuerId());
        } else if ("pix".equals(input.paymentMethod())) {
            requestBuilder
                    .paymentMethodId("pix");
        }

        try {
            PaymentClient paymentClient = new PaymentClient();
            Payment payment = paymentClient.create(requestBuilder.build());

            order.setPaymentId(payment.getId().toString());
            order.setPaymentStatus(payment.getStatus());

            switch (payment.getStatus()) {
                case "approved" -> order.setStatus(OrderStatus.PAID);
                case "rejected" -> order.setStatus(OrderStatus.CANCELLED);
            }

            orderRepository.save(order);

            log.info("Pagamento processado — Pedido: {}, Payment: {}, Status: {}",
                    order.getId(), payment.getId(), payment.getStatus());

            String pixQrCode = null;
            String pixQrCodeBase64 = null;

            if ("pix".equals(input.paymentMethod()) &&
                    payment.getPointOfInteraction() != null &&
                    payment.getPointOfInteraction().getTransactionData() != null) {
                pixQrCode = payment.getPointOfInteraction().getTransactionData().getQrCode();
                pixQrCodeBase64 = payment.getPointOfInteraction().getTransactionData().getQrCodeBase64();
            }

            return new PaymentOutput(
                    payment.getId().toString(),
                    payment.getStatus(),
                    payment.getStatusDetail(),
                    input.paymentMethod(),
                    order.getTotal(),
                    pixQrCode,
                    pixQrCodeBase64
            );

        } catch (com.mercadopago.exceptions.MPApiException e) {
            log.error("MP API Error - Status: {}, Content: {}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            throw new RuntimeException("Erro ao processar pagamento", e);
        } catch (Exception e) {
            log.error("Erro ao processar pagamento do pedido {}", input.orderId(), e);
            throw new RuntimeException("Erro ao processar pagamento", e);
        }
    }

    public void processWebhook(String paymentId, UUID orderId) {
        try {
            PaymentClient paymentClient = new PaymentClient();
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            String status = payment.getStatus();

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

            order.setPaymentId(paymentId);
            order.setPaymentStatus(status);

            switch (status) {
                case "approved" -> order.setStatus(OrderStatus.PAID);
                case "rejected" -> order.setStatus(OrderStatus.CANCELLED);
            }

            orderRepository.save(order);
            log.info("Webhook processado - Pedido: {}, Payment: {}, Status: {}",
                    orderId, paymentId, status);

        } catch (Exception e) {
            log.error("Erro ao processar webhook do pedido {}", orderId, e);
            throw new RuntimeException("Erro ao processar webhook", e);
        }
    }
}

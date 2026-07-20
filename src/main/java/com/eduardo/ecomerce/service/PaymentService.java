package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
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

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    @Value("${mercadopago.back-url.success}")
    private String successUrl;

    @Value("${mercadopago.back-url.failure}")
    private String failureUrl;

    public String createCheckout(UUID orderId) {

        log.info("MP URLs — success: [{}], failure: [{}], notification: [{}]",
                successUrl, failureUrl, notificationUrl);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido nao encontrado"));

        List<PreferenceItemRequest> items = order.getItems().stream()
                .map(this::toPreferenceItem)
                .toList();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(successUrl)
                .failure(failureUrl)
                .pending(successUrl)
                .build();

        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .notificationUrl(notificationUrl + "?orderId=" + orderId)

                .externalReference(orderId.toString())
                .build();

        try {
            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);

            order.setCheckoutUrl(preference.getInitPoint());
            orderRepository.save(order);

            log.info("Checkout criado para pedido {}: {}", orderId, preference.getInitPoint());

            return preference.getInitPoint();

        } catch (com.mercadopago.exceptions.MPApiException e) {
            log.error("MP API Error - Status: {}, Content: {}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            throw new RuntimeException("Erro ao criar checkout no Mercado Pago", e);
        } catch (Exception e) {
            log.error("Erro ao criar checkout para pedido {}", orderId, e);
            throw new RuntimeException("Erro ao criar checkout no Mercado Pago", e);
        }
    }

    public void processWebhook(String paymentId, UUID orderId) {
        try {
            com.mercadopago.client.payment.PaymentClient paymentClient =
                    new com.mercadopago.client.payment.PaymentClient();

            com.mercadopago.resources.payment.Payment payment =
                    paymentClient.get(Long.parseLong(paymentId));

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

    private PreferenceItemRequest toPreferenceItem(OrderItem item) {
        return PreferenceItemRequest.builder()
                .title(item.getVariant().getProduct().getName() + " - " + item.getVariant().getSize())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .currencyId("BRL")
                .build();
    }
}

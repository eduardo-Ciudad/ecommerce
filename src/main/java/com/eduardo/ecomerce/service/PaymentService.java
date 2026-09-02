package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    private static final Set<String> CANCELLABLE_PAYMENT_STATUSES = Set.of("pending", "in_process", "authorized");

    public PaymentCancellationOutcome tryCancelPendingPayment(String paymentId) {
        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            if (!CANCELLABLE_PAYMENT_STATUSES.contains(payment.getStatus())) {
                log.info("Payment {} não está em status cancelável para expiração (status atual: {})",
                        paymentId, payment.getStatus());
                return PaymentCancellationOutcome.NOT_CANCELLABLE;
            }

            paymentClient.cancel(Long.parseLong(paymentId));
            log.info("Payment {} cancelado no Mercado Pago (expiração de pedido)", paymentId);
            return PaymentCancellationOutcome.CANCELLED;

        } catch (Exception e) {
            log.error("Falha ao tentar cancelar payment {} no Mercado Pago (expiração de pedido)", paymentId, e);
            return PaymentCancellationOutcome.FAILED;
        }
    }


    @Transactional
    public PaymentOutput processPayment(PaymentInput input, String payerEmail) {
        Order order = orderRepository.findByIdForUpdate(input.orderId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (!order.getUser().getEmail().equals(payerEmail)) {
            throw new BusinessException("Pedido não pertence ao usuário autenticado");
        }

        if (order.getPaymentId() != null) {
            throw new BusinessException("Pedido já possui pagamento em processamento");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Pedido não está pendente de pagamento");
        }


        PaymentCreateRequest.PaymentCreateRequestBuilder requestBuilder = PaymentCreateRequest.builder()
                .transactionAmount(order.getTotal())
                .description("MiniModa - Pedido #" + order.getId().toString().substring(0, 8))
                .externalReference(order.getId().toString())
                .notificationUrl(notificationUrl)
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
            throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        } catch (Exception e) {
            log.error("Erro ao processar pagamento do pedido {}", input.orderId(), e);
            throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        }
    }


    @Transactional
    public void processWebhook(String paymentId) {
        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            String status = payment.getStatus();
            UUID orderId = UUID.fromString(payment.getExternalReference());

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

            if (paymentId.equals(order.getPaymentId()) && status.equals(order.getPaymentStatus())) {
                log.info("Webhook ignorado - Pedido: {}, Payment: {} já processado com status {}",
                        orderId, paymentId, status);
                return;
            }

            boolean paymentIdMatches = order.getPaymentId() == null || paymentId.equals(order.getPaymentId());
            if (!paymentIdMatches) {
                log.warn("Webhook ignorado - Pedido: {} está associado ao payment {}, mas recebeu notificação do payment {} (status {})",
                        orderId, order.getPaymentId(), paymentId, status);
                return;
            }

            if (order.getStatus() != OrderStatus.PENDING) {
                log.warn("Webhook recebido para pedido {} fora de PENDING (status atual: {}) - payment: {}, status recebido: {}. Nenhuma alteração aplicada.",
                        orderId, order.getStatus(), paymentId, status);
                return;
            }

            BigDecimal paidAmount = payment.getTransactionAmount();
            if ("approved".equals(status) && paidAmount.compareTo(order.getTotal()) != 0) {
                log.error("Valor divergente no webhook - Pedido: {}, esperado: {}, pago: {}",
                        orderId, order.getTotal(), paidAmount);
                throw new BusinessException("Valor do pagamento não corresponde ao pedido");
            }

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
            log.error("Erro ao processar webhook do payment {}", paymentId, e);
            throw new BusinessException("Erro ao processar notificação de pagamento");
        }
    }

    public enum PaymentCancellationOutcome {
        NOT_CANCELLABLE,
        CANCELLED,
        FAILED
    }


}
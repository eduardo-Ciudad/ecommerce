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
import com.mercadopago.net.MPSearchRequest;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentClient paymentClient;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    private static final Set<String> CANCELLABLE_PAYMENT_STATUSES = Set.of("pending", "in_process", "authorized");
    private static final Set<String> KNOWN_PAYMENT_STATUSES =
            Set.of("approved", "rejected", "pending", "in_process", "authorized");

    public enum PaymentCancellationOutcome {
        NOT_CANCELLABLE,
        CANCELLED,
        FAILED
    }

    public enum OrphanedPaymentOutcome {
        NOT_FOUND,
        FOUND,
        AMBIGUOUS,
        FAILED
    }

    public record OrphanedPaymentLookup(OrphanedPaymentOutcome outcome, Payment payment) {
        public static OrphanedPaymentLookup notFound() {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.NOT_FOUND, null);
        }

        public static OrphanedPaymentLookup found(Payment payment) {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.FOUND, payment);
        }

        public static OrphanedPaymentLookup ambiguous() {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.AMBIGUOUS, null);
        }

        public static OrphanedPaymentLookup failed() {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.FAILED, null);
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

        OrphanedPaymentLookup lookup = findExistingPaymentForOrder(order.getId(), order.getTotal());

        Payment payment = switch (lookup.outcome()) {
            case FOUND -> {
                log.info("Pagamento existente encontrado no Mercado Pago para o pedido {} — reaproveitando em vez de criar um novo",
                        order.getId());
                yield lookup.payment();
            }
            case NOT_FOUND -> createNewPayment(order, input, payerEmail);
            case AMBIGUOUS -> throw new BusinessException(
                    "Não foi possível confirmar o pagamento deste pedido. Entre em contato com o suporte.");
            case FAILED -> throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        };

        order.setPaymentId(payment.getId().toString());
        order.setPaymentStatus(payment.getStatus());

        switch (payment.getStatus()) {
            case "approved" -> order.setStatus(OrderStatus.PAID);
            case "rejected" -> {
                order.setStatus(OrderStatus.CANCELLED);
                orderService.restoreStock(order);
            }
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
    }

    private Payment createNewPayment(Order order, PaymentInput input, String payerEmail) {
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
            return paymentClient.create(requestBuilder.build());
        } catch (com.mercadopago.exceptions.MPApiException e) {
            log.error("MP API Error - Status: {}, Content: {}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        } catch (Exception e) {
            log.error("Erro ao processar pagamento do pedido {}", order.getId(), e);
            throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        }
    }

    public OrphanedPaymentLookup findExistingPaymentForOrder(UUID orderId, BigDecimal expectedTotal) {
        try {
            MPSearchRequest searchRequest = MPSearchRequest.builder()
                    .filters(Map.of("external_reference", orderId.toString()))
                    .limit(10)
                    .offset(0)
                    .build();

            List<Payment> results = paymentClient.search(searchRequest).getResults();

            List<Payment> matching = results.stream()
                    .filter(p -> orderId.toString().equals(p.getExternalReference()))
                    .filter(p -> p.getTransactionAmount() != null
                            && p.getTransactionAmount().compareTo(expectedTotal) == 0)
                    .filter(p -> KNOWN_PAYMENT_STATUSES.contains(p.getStatus()))
                    .toList();

            if (matching.isEmpty()) {
                return OrphanedPaymentLookup.notFound();
            }

            if (matching.size() > 1) {
                log.error("Mais de um pagamento válido encontrado no Mercado Pago para o pedido {} — requer investigação manual",
                        orderId);
                return OrphanedPaymentLookup.ambiguous();
            }

            return OrphanedPaymentLookup.found(matching.get(0));

        } catch (Exception e) {
            log.error("Falha ao buscar pagamento existente no Mercado Pago para o pedido {}", orderId, e);
            return OrphanedPaymentLookup.failed();
        }
    }



    @Transactional
    public void processWebhook(String paymentId) {
        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            String status = payment.getStatus();
            UUID orderId = UUID.fromString(payment.getExternalReference());

            Order order = orderRepository.findByIdForUpdate(orderId)
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
                case "rejected" -> {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderService.restoreStock(order);
                }
            }

            orderRepository.save(order);
            log.info("Webhook processado - Pedido: {}, Payment: {}, Status: {}",
                    orderId, paymentId, status);

        } catch (Exception e) {
            log.error("Erro ao processar webhook do payment {}", paymentId, e);
            throw new BusinessException("Erro ao processar notificação de pagamento");
        }
    }

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




}